# valve-butler

valve-butler는 Jenkinfile에서 사용하는 groovy 스크립트를 모듈로 제공합니다.

제공되는 주요 기능은 다음과 같습니다.
* make_chart
  * Helm chart를 생성합니다.
* build_chart
  * Helm chart를 chart repository에 푸시합니다.
* build_image
  * 도커 이미지를 생성합니다.
* deploy
  * 쿠버네티스 환경에 배포 합니다.
* rollback
  * 이전 배포 버전으로 원복 합니다.
* remove
  * 배포된 helm 리소스를 전체 삭제 합니다.
* slack
  * 작업 결과를 슬랙으로 통지합니다.

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
