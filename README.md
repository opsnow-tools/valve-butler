# pipeline

```groovy
// Requires [Pipeline: GitHub Groovy Libraries]
// https://plugins.jenkins.io/pipeline-github-lib
@Library('github.com/opspresso/pipeline@master')
def pipeline = new com.opspresso.Pipeline()

pipeline.prepare()
pipeline.scan()
```

* <https://jenkins.io/doc/pipeline/steps/workflow-basic-steps/>
