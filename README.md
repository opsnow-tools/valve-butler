# valve-butler

```groovy
// Requires [Pipeline: GitHub Groovy Libraries]
// https://plugins.jenkins.io/pipeline-github-lib
@Library("github.com/opsnow-tools/valve-butler")
def butler = new com.opsnow.valve.Butler()

butler.prepare()
butler.scan()
```

* <https://jenkins.io/doc/pipeline/steps/workflow-basic-steps/>
