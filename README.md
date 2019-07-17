# valve-butler

valve-butler는 Jenkinfile에서 사용하는 groovy 스크립트를 모듈로 제공합니다.

제공되는 주요 기능은 다음과 같습니다.
* make_chart
* build_chart
* build_image
* deploy
* rollback
* remove
* slack

## 사용 방법
Jenkinfile 내에서는 다음과 같이 사용할 수 있습니다.
```groovy
// Requires [Pipeline: GitHub Groovy Libraries]
// https://plugins.jenkins.io/pipeline-github-lib
@Library("github.com/opsnow-tools/valve-butler")
def butler = new com.opsnow.valve.Butler()

butler.prepare()
butler.scan()
```
## 참고 자료
* <https://jenkins.io/doc/pipeline/steps/workflow-basic-steps/>
