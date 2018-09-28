#!/usr/bin/groovy
package com.opsnow.valve;

def prepare(namespace = "devops") {
    this.base_domain = ""
    this.slack_token = ""
    this.helm_state = ""

    // domains
    this.jenkins = scan_domain("jenkins", namespace)
    if (this.jenkins) {
        this.base_domain = this.jenkins.substring(this.jenkins.indexOf('.') + 1)
    }

    this.chartmuseum = scan_domain("chartmuseum", namespace)
    this.registry = scan_domain("docker-registry", namespace)
    this.sonarqube = scan_domain("sonarqube", namespace)
    this.nexus = scan_domain("sonatype-nexus", namespace)

    // slack token
    scan_slack_token()
}

def scan(name = "sample", branch = "master", source_lang = "") {
    this.source_lang = source_lang
    this.source_root = "."

    // version
    if (branch == "master") {
        date = new Date().format('yyyyMMdd-HHmm')
        version = "v0.1.1-$date"
    } else {
        version = "v0.0.1"
    }

    this.version = version
    echo "# version: $version"

    // language
    if (!this.source_lang || this.source_lang == "java") {
        scan_langusge("pom.xml", "java")
    }
    if (!this.source_lang || this.source_lang == "nodejs") {
        scan_langusge("package.json", "nodejs")
    }

    // chart
    make_chart(name, version)
}

def scan_langusge(target = "", source_lang = "") {
    def target_path = sh(script: "find . -name $target | head -1", returnStdout: true).trim()

    if (target_path) {
        def source_root = sh(script: "dirname $target_path", returnStdout: true).trim()

        if (source_root) {
            this.source_lang = source_lang
            this.source_root = source_root

            echo "# source_lang: $source_lang"
            echo "# source_root: $source_root"

            // maven mirror
            if (source_lang == 'java') {
                if (this.nexus) {
                    def home = "/home/jenkins"

                    sh "mkdir -p $home/.m2"
                    sh "cp -f /root/.m2/settings.xml $home/.m2/settings.xml | true"

                    def public_url = "http://${this.nexus}/repository/maven-public/"
                    def mirror_xml = "<mirror><id>mirror</id><url>${public_url}</url><mirrorOf>*</mirrorOf></mirror>"

                    echo "# maven-public: $public_url"

                    sh "sed -i -e \"s|<!-- ### configured mirrors ### -->|${mirror_xml}|\" $home/.m2/settings.xml | true"
                }
            }
        }
    }
}

def scan_domain(target = "", namespace = "") {
    if (!target) {
        throw new RuntimeException("target is null.")
    }
    if (!namespace) {
        throw new RuntimeException("namespace is null.")
    }

    def domain = sh(script: "kubectl get ing -n $namespace -o wide | grep $target | awk '{print \$2}'", returnStdout: true).trim()

    if (domain && this.base_domain) {
        domain = "$target-$namespace.${this.base_domain}"
    }

    echo "# $target: $domain"

    return domain
}

def scan_slack_token(namespace = "devops") {
    def token = sh(script: "kubectl get secret slack-token -n $namespace -o json | jq -r .data.text | base64 -d", returnStdout: true).trim()
    if (token) {
        echo "# slack-token: $token"
        this.slack_token = token
    }
}

def env_cluster(cluster = "", namespace = "devops") {
    if (!cluster) {
        throw new RuntimeException("cluster is null.")
    }

    // check cluster secret
    count = sh(script: "kubectl get secret -n $namespace | grep 'kube-config-$cluster' | wc -l", returnStdout: true).trim()
    if (count == 0) {
        throw new RuntimeException("cluster is null.")
    }

    sh "mkdir -p $home/.kube"
    sh "kubectl get secret kube-config-$cluster -n $namespace -o json | jq -r .data.text | base64 -d > $home/.kube/config"

    // sh "kubectl cluster-info"
    sh "kubectl config current-context"

    // check current context
    count = sh(script: "kubectl config current-context | grep '$cluster' | wc -l", returnStdout: true).trim()
    if (count == 0) {
        throw new RuntimeException("current-context is not match.")
    }
}

def env_apply(type = "", name = "", namespace = "", cluster = "") {
    if (!type) {
        throw new RuntimeException("type is null.")
    }
    if (!name) {
        throw new RuntimeException("name is null.")
    }
    if (!namespace) {
        throw new RuntimeException("namespace is null.")
    }

    // check namespace
    count = sh(script: "kubectl get ns $namespace 2>&1 | grep $namespace | grep Active | wc -l", returnStdout: true).trim()
    if (count == 0) {
        sh "kubectl create namespace $namespace"
    }

    // env yaml
    def env = ""
    if (cluster) {
        env = sh(script: "find . -name ${type}.yaml | grep env/$cluster/$namespace/${type}.yaml | head -1", returnStdout: true).trim()
    } else {
        env = sh(script: "find . -name ${type}.yaml | grep env/$namespace/${type}.yaml | head -1", returnStdout: true).trim()
    }

    // env apply
    if (env) {
        sh "sed -i -e \"s|name: REPLACE-FULLNAME|name: $name-$namespace|\" $env"
        sh "kubectl apply -n $namespace -f $env"
        return "true"
    }
    return "false"
}

def make_chart(name = "", version = "") {
    if (!name) {
        throw new RuntimeException("name is null.")
    }
    if (!version) {
        throw new RuntimeException("version is null.")
    }

    def chart = sh(script: "find . -name Chart.yaml | head -1", returnStdout: true).trim()
    if (chart) {
        // sh "mv charts/acme charts/$name"

        dir("charts/$name") {
            sh "sed -i -e \"s/name: .*/name: $name/\" Chart.yaml"
            sh "sed -i -e \"s/version: .*/version: $version/\" Chart.yaml"

            sh "sed -i -e \"s|tag: .*|tag: $version|\" values.yaml"

            if (registry) {
                sh "sed -i -e \"s|repository: .*|repository: $registry/$name|\" values.yaml"
            }

            if (base_domain) {
                sh "sed -i -e \"s|basedomain: .*|basedomain: $base_domain|\" values.yaml"
            }
        }
    }
}

def build_chart(name = "", version = "") {
    if (!name) {
        throw new RuntimeException("name is null.")
    }
    if (!version) {
        throw new RuntimeException("version is null.")
    }

    helm_init()

    def helm_push = sh(script: "helm plugin list | grep 'Push chart package' | wc -l", returnStdout: true).trim()
    if (helm_push == '0') {
        sh "helm plugin install https://github.com/chartmuseum/helm-push"
    }
    sh "helm plugin list"

    def chart = sh(script: "find . -name Chart.yaml | head -1", returnStdout: true).trim()
    if (chart) {
        dir("charts/$name") {
            sh "helm lint ."

            if (chartmuseum) {
                sh "helm push . chartmuseum"
            }
        }
    }

    sh "helm repo update"
    sh "helm search $name"
}

def build_image(name = "", version = "") {
    if (!name) {
        throw new RuntimeException("name is null.")
    }
    if (!version) {
        throw new RuntimeException("version is null.")
    }

    sh """
        docker build -t $registry/$name:$version .
        docker push $registry/$name:$version
    """
}

def helm_init() {
    if (this.helm_state) {
        return
    }

    sh "helm init"
    sh "helm version"

    if (chartmuseum) {
        sh "helm repo add chartmuseum https://$chartmuseum"
    }

    sh "helm repo list"
    sh "helm repo update"

    this.helm_state = "initialized"
}

def helm_install(name = "", version = "", namespace = "", base_domain = "", cluster = "") {
    if (!name) {
        throw new RuntimeException("name is null.")
    }
    if (!version) {
        throw new RuntimeException("version is null.")
    }
    if (!namespace) {
        throw new RuntimeException("namespace is null.")
    }

    profile = "$namespace"

    if (cluster) {
        env_cluster(cluster)
        // profile = "$cluster-$namespace"
    }

    if (!base_domain) {
        base_domain = this.base_domain
    }

    configmap = env_apply("configmap", "$name", "$namespace", "$cluster")
    secret = env_apply("secret", "$name", "$namespace", "$cluster")

    helm_init()

    if (version == "latest") {
        version = sh(script: "helm search chartmuseum/$name | grep $name | awk '{print \$2}'", returnStdout: true).trim()
        if (!version) {
            throw new RuntimeException("version is null.")
        }
    }

    sh """
        helm upgrade --install $name-$namespace chartmuseum/$name \
                     --version $version --namespace $namespace --devel \
                     --set fullnameOverride=$name-$namespace \
                     --set ingress.basedomain=$base_domain \
                     --set configmap.enabled=$configmap \
                     --set secret.enabled=$secret \
                     --set profile=$profile
    """

    sh "helm search $name"
    sh "helm history $name-$namespace --max 5"
}

def helm_delete(name = "", namespace = "", cluster = "") {
    if (!name) {
        throw new RuntimeException("name is null.")
    }
    if (!namespace) {
        throw new RuntimeException("namespace is null.")
    }

    if (cluster) {
        env_cluster(cluster)
    }

    helm_init()

    sh "helm search $name"
    sh "helm history $name-$namespace --max 5"

    sh "helm delete --purge $name-$namespace"
}

def draft_init() {
    sh "draft init"
    sh "draft version"

    if (registry) {
        sh "draft config set registry $registry"
    }
}

def draft_up(name = "", namespace = "", base_domain = "", cluster = "") {
    if (!name) {
        throw new RuntimeException("name is null.")
    }
    if (!namespace) {
        throw new RuntimeException("namespace is null.")
    }

    if (cluster) {
        env_cluster(cluster)
    }
    if (!base_domain) {
        base_domain = this.base_domain
    }

    draft_init()

    sh "sed -i -e \"s/NAMESPACE/$namespace/g\" draft.toml"
    sh "sed -i -e \"s/NAME/$name-$namespace/g\" draft.toml"

    sh "draft up -e $namespace"

    sh "draft logs"
}

def npm_build() {
    def source_root = this.source_root
    dir("$source_root") {
        sh "npm run build"
    }
}

def mvn_build() {
    def source_root = this.source_root
    dir("$source_root") {
        if (this.nexus) {
            sh "mvn package -s /home/jenkins/.m2/settings.xml -DskipTests=true"
        } else {
            sh "mvn package -DskipTests=true"
        }
    }
}

def mvn_test() {
    def source_root = this.source_root
    dir("$source_root") {
        if (this.nexus) {
            sh "mvn test -s /home/jenkins/.m2/settings.xml"
        } else {
            sh "mvn test"
        }
    }
}

def mvn_deploy() {
    def source_root = this.source_root
    dir("$source_root") {
        if (this.nexus) {
            sh "mvn deploy -s /home/jenkins/.m2/settings.xml -DskipTests=true"
        } else {
            sh "mvn deploy -DskipTests=true"
        }
    }
}

def mvn_sonar() {
    def sonarqube = this.sonarqube
    if (sonarqube) {
        def source_root = this.source_root
        dir("$source_root") {
            if (this.nexus) {
                sh "mvn sonar:sonar -s /home/jenkins/.m2/settings.xml -Dsonar.host.url=http://$sonarqube -DskipTests=true"
            } else {
                sh "mvn sonar:sonar -Dsonar.host.url=$sonarqube -DskipTests=true"
            }
        }
    }
}

def slack(token = "", color = "", title = "", message = "", footer = "") {
    if (token) {
        try {
            sh """
                curl -sL toast.sh/helper/slack.sh | bash -s -- --token='$token' \
                --color='$color' --title='$title' --footer='$footer' '$message'
            """
        } catch (ignored) {
        }
    }
}
