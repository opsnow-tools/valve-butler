#!/usr/bin/groovy
package com.opspresso;

def scan(name = "", branch = "master", namespace = "devops", base_domain = "") {
    this.name = name
    this.branch = branch
    this.namespace = namespace
    this.cluster = ""
    this.source_lang = ""
    this.source_root = ""
    this.base_domain = base_domain

    // version
    if (branch == "master") {
        date = new Date().format('yyyyMMdd-HHmm')
        version = "0.1.1-$date"
    } else {
        version = "v0.0.0"
    }

    this.version = version
    echo "# version: $version"

    // domains
    this.jenkins = scan_domain("jenkins", namespace)
    if (this.jenkins) {
        this.base_domain = this.jenkins.substring(this.jenkins.indexOf('.') + 1)
    }

    this.chartmuseum = scan_domain("chartmuseum", namespace)
    this.registry = scan_domain("docker-registry", namespace)
    this.sonarqube = scan_domain("sonarqube", namespace)
    this.nexus = scan_domain("sonatype-nexus", namespace)

    // language
    if (!this.source_lang?.trim()) {
        scan_langusge("pom.xml", "java")
    }
    if (!this.source_lang?.trim()) {
        scan_langusge("package.json", "nodejs")
    }
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
    if (!target?.trim()) {
        throw new RuntimeException("target is null.")
    }
    if (!namespace?.trim()) {
        throw new RuntimeException("namespace is null.")
    }

    def domain = sh(script: "kubectl get ing -n $namespace -o wide | grep $target | awk '{print \$2}'", returnStdout: true).trim()

    if (domain && this.base_domain) {
        domain = "$target-$namespace.${this.base_domain}"
    }

    echo "# $target: $domain"

    return domain
}

def build_image(name = "", version = "", registry = "") {
    if (!name?.trim()) {
        throw new RuntimeException("name is null.")
    }
    if (!version?.trim()) {
        throw new RuntimeException("version is null.")
    }
    if (!registry?.trim()) {
        throw new RuntimeException("registry is null.")
    }

    sh """
        docker build -t $registry/$name:$version .
        docker push $registry/$name:$version
    """
}

def build_chart(name = "", version = "", registry = "", base_domain = "") {
    if (!name?.trim()) {
        throw new RuntimeException("name is null.")
    }
    if (!version?.trim()) {
        throw new RuntimeException("version is null.")
    }
    if (!registry?.trim()) {
        throw new RuntimeException("registry is null.")
    }

    helm_init()

    sh "mv charts/acme charts/$name"

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

        sh "helm lint ."

        if (chartmuseum) {
            sh "helm push . chartmuseum"
        }
    }

    sh "helm repo update"
    sh "helm search $name"
}

def helm_init() {
    sh "helm init"
    sh "helm version"

    if (chartmuseum) {
        sh "helm repo add chartmuseum https://$chartmuseum"
    }

    sh "helm repo list"
    sh "helm repo update"

    helm_push = sh(script: "helm plugin list | grep 'Push chart package' | wc -l", returnStdout: true).trim()
    if (helm_push == '0') {
        sh "helm plugin install https://github.com/chartmuseum/helm-push"
    }

    sh "helm plugin list"
}

def helm_install(name = "", version = "", namespace = "", cluster = "") {
    if (!name?.trim()) {
        throw new RuntimeException("name is null.")
    }
    if (!version?.trim()) {
        throw new RuntimeException("version is null.")
    }
    if (!namespace?.trim()) {
        throw new RuntimeException("namespace is null.")
    }

    helm_init()

    sh """
        helm upgrade --install $name-$namespace chartmuseum/$name \
                    --version $version --namespace $namespace --devel \
                    --set fullnameOverride=$name-$namespace

        helm history $name-$namespace --max 5
    """
}

def helm_delete(name = "", namespace = "", cluster = "") {
    if (!name?.trim()) {
        throw new RuntimeException("name is null.")
    }
    if (!namespace?.trim()) {
        throw new RuntimeException("namespace is null.")
    }

    helm_init()

    sh """
        helm search $name
        helm history $name-$namespace --max 5
        helm delete --purge $name-$namespace
    """
}

def draft_init() {
    sh "draft init"
    sh "draft version"

    if (registry) {
        sh "draft config set registry $registry"
    }
}

def draft_up(name = "", namespace = "", cluster = "") {
    if (!name?.trim()) {
        throw new RuntimeException("name is null.")
    }
    if (!namespace?.trim()) {
        throw new RuntimeException("namespace is null.")
    }

    helm_init()

    draft_init()

    sh "sed -i -e \"s/NAMESPACE/$namespace/g\" draft.toml"
    sh "sed -i -e \"s/NAME/$name-$namespace/g\" draft.toml"

    sh "draft up -e $namespace"
}
