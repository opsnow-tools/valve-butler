# pipeline

```groovy
// Requires [Pipeline: GitHub Groovy Libraries]
// https://plugins.jenkins.io/pipeline-github-lib
@Library('github.com/opsnow/pipeline@master')
def pipeline = new com.opsnow.Pipeline()

pipeline.scan()
```

* <https://jenkins.io/doc/pipeline/steps/workflow-basic-steps/>
