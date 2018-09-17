# valve-butler

```groovy
// Requires [Pipeline: GitHub Groovy Libraries]
// https://plugins.jenkins.io/pipeline-github-lib
@Library("github.com/opsnow-tools/valve-butler")
def pump = new com.opsnow.valve.Butler()

pump.prepare()
pump.scan()
```

* <https://jenkins.io/doc/pipeline/steps/workflow-basic-steps/>
