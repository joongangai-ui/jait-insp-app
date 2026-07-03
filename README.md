# 중앙아이티 점검 — 안드로이드 앱 (WebView 래퍼)

기존 모바일 웹(`m.php`)을 그대로 띄우는 가벼운 안드로이드 앱입니다.
카메라·서명·사진 첨부·파일 업로드·다운로드가 모두 동작하고, **웹을 고치면 앱도 자동으로 바뀝니다**(앱 재배포 불필요).

- 앱 이름: **중앙아이티 점검**
- 여는 주소: `https://jaitpms.com/m.php`  ← 바꾸려면 `app/src/main/java/com/joongang/insp/MainActivity.java` 의 `START_URL` 한 줄만 수정
- 설치 방식: APK 직접 설치(사이드로드)

---

## APK 받는 법 — 3가지 중 편한 것

### ① GitHub Actions (추천 · 컴퓨터에 아무것도 설치 안 함)
1. github.com 로그인 → **New repository**(이름 아무거나, Private 가능) 생성
2. 이 폴더 전체를 그 저장소에 업로드(드래그&드롭 또는 git push)
3. 저장소 상단 **Actions** 탭 → 자동으로 "Build APK"가 돌아감(2~4분)
4. 끝나면 그 실행 화면 아래 **Artifacts → joongang-insp-apk** 다운로드 → 압축 안에 **app-debug.apk**
5. 그 APK를 직원 폰에 보내 설치(아래 "폰에 설치하기")

> 한 번 올려두면, 이후 `START_URL` 등을 고쳐 다시 올릴 때마다 APK가 자동으로 새로 나옵니다.

### ② 안드로이드 스튜디오 (개발 PC가 있으면)
1. Android Studio 에서 **Open** → 이 폴더 선택
2. 메뉴 **Build → Build App Bundle(s)/APK(s) → Build APK(s)**
3. 완료 알림의 **locate** → `app/build/outputs/apk/debug/app-debug.apk`

### ③ 개발자에게 이 폴더 전달
표준 Gradle 안드로이드 프로젝트라, 누구든 `./gradlew assembleDebug` 한 줄로 빌드됩니다.

---

## 폰에 설치하기 (APK 직접 설치)
1. `app-debug.apk` 를 카톡·메일·USB 등으로 폰에 전달
2. 파일 탭 → "출처를 알 수 없는 앱 설치" 허용(처음 1회) → 설치
3. 첫 실행 시 카메라·알림 권한 허용

> debug 서명 APK라 사내·직원 폰 직접 설치에 적합합니다. 구글 플레이 정식 등록이 필요해지면 release 서명으로 전환하면 됩니다(안내 가능).

---

## 다음 단계 — 푸시 알림(점검 배정·서명완료)
앱은 푸시를 받을 수 있게 권한(POST_NOTIFICATIONS)까지 준비돼 있습니다.
서버가 보내는 푸시는 **Firebase(FCM)** 가 필요해요(무료):
1. console.firebase.google.com 에서 프로젝트 1개 생성 → 안드로이드 앱 추가(패키지명 `com.joongang.insp`)
2. 받은 `google-services.json` 을 전달해 주시면 → 앱에 FCM 연동 + 서버(PHP)에 토큰 저장·발송 엔드포인트를 붙여 드립니다.

---

## 구조
```
settings.gradle / build.gradle / gradle.properties   ← Gradle 설정
app/build.gradle                                     ← 앱 모듈 설정(패키지·SDK·의존성)
app/src/main/AndroidManifest.xml                     ← 권한·액티비티·FileProvider
app/src/main/java/.../MainActivity.java              ← WebView 본체(여기 START_URL)
app/src/main/res/                                    ← 아이콘·색·테마·설정 xml
.github/workflows/build-apk.yml                      ← 클라우드 자동 빌드
```
