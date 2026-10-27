# 스토리 드라이브 (Story Drive)

차의 신호와 내비 경로로 이야기가 펼쳐지는 Pleos Connect 전용 오디오 동화 앱입니다. 뒷좌석 아이는 벨트를 매고 목소리로 이야기에 참여하고, 운전자는 운전에만 집중할 수 있습니다.

## 실행 방법

1. Pleos Connect 에뮬레이터를 켭니다.
2. APK를 설치하고 실행합니다.

   ```bash
   adb install -g apk/SooJeongMin_StoryDrive_release.apk
   adb shell am start -n com.soojeongmin.storydrive/.ui.MainActivity
   ```

3. 마이크 안내와 보호자 동의를 확인한 뒤, 좌석을 눌러 탐험대원을 등록합니다.
4. Virtual Console에서 좌석 착좌와 안전벨트를 켜면 벨트 미션이 시작됩니다.
5. 기어를 D로 바꾸면 이야기가 시작됩니다. 내비에 목적지를 설정하면 터널, 휴게소, 정체, 도착이 이야기 속 장면으로 이어집니다.

## 테스트 환경 및 필요한 설정

- Pleos Connect 에뮬레이터 (Android Automotive 14, 2560×1440)
- Vehicle SDK 2.3.2, NaviHelper SDK 3.0.5, TextToSpeech SDK 3.3.1, SpeechToText SDK 3.3.1 (TTS와 STT는 ON_DEVICE 모드로 동작하므로 별도 인증 정보가 필요 없습니다.)
- 권한: 마이크, 알림, 차량 정보, 내비 경로 (`adb install -g`로 설치하면 모두 허용됩니다.)
- Virtual Console 연결: `adb forward tcp:8088 tcp:8088`
- 마이크 연결: `adb emu avd hostmicon`

## APK 파일 위치

`apk/SooJeongMin_StoryDrive_release.apk`
