# pipeline

```groovy
// Requires [Pipeline: GitHub Groovy Libraries]
// https://plugins.jenkins.io/pipeline-github-lib
@Library('github.com/opspresso/jenkins-pipeline@master')
def helm = new com.opsnow.pipeline.Helm()

helm.init()
```
