# UCTale(User Create Tale)

Gemini API와 Spring Boot를 활용한 웹 기반 인터랙티브 텍스트 어드벤처 게임입니다.
사용자가 설정한 자유로운 세계관 속에서 AI가 실시간으로 스토리와 이미지를 생성합니다.

## 🚀 프로젝트 개요
* **주제:** AI 생성형 TRPG
* **핵심 기술:** Gemini 2.5 Flash (텍스트), Pollinations.ai (이미지), Java Spring Boot


## 🛠 기술 스택
* **Frontend:** React.js, Vercel (배포)
* **Backend:** Java 21, Spring Boot, Render (배포)
* **Database:** H2 Database
* **AI Model:** Text: Google Gemini 2.5 Flash, Image: Pollinations.ai (Flux Model)

## 📋 기능 목록

### 1. 환경 설정 및 기본 세팅
- [x] **프로젝트 초기화**
    - Spring Boot 프로젝트 생성 (Java 21)
    - `.gitignore` 및 `build.gradle` 설정
    - Google AI Studio API Key 환경 변수 설정
- [x] **DB 설정**
    - H2 Console 연동 및 JPA 설정

### 2. 게임 초기화 (Game Init)
- [x] **DTO 및 도메인 설계**
    - `GameInitRequest` (세계관, 사용자 정보)
    - `GameResponse` (스토리, 선택지, 이미지 URL)
- [x] **Gemini 텍스트 생성 로직**
    - Gemini Client 구현 (REST API 호출)
    - System Instruction 설정: 오프닝 스토리 및 선택지 3개 생성
    - `JSON Mode` 적용: 응답 형식을 구조화된 JSON으로 강제
- [x] **이미지 생성 로직 (Pollinations.ai)**
    - Flux 모델 적용하여 흑백 스케치(Charcoal Sketch) 스타일 구현
    - 오프닝 배경 이미지 생성
    - 비동기 처리 또는 순차 처리 구조 확립

### 3. 게임 진행 (Game Progress)
- [ ] **스토리 진행 API**
    - 사용자 선택(Choice ID) 수신 및 처리
    - 이전 게임 상태(`game_state`)를 포함하여 Gemini에 다음 턴 요청
- [ ] **동적 이미지 판단 로직 (핵심)**
    - Gemini 응답 내 `visual_assets` 리스트 파싱
    - **판단:** 리스트가 비어있으면 이미지 생성 건너뛰기 (자원 절약)
    - **실행:** 리스트가 있으면 해당 프롬프트에 스타일 접미사를 붙여 이미지 생성

### 4. 게임 상태 관리 (State Management)
- [ ] **세션 관리**
    - 사용자별 고유 Session ID 발급
    - H2 DB에 턴별 스토리 및 상태(Inventory 등) 저장
- [ ] **게임 리셋**
    - '처음으로' 요청 시 해당 세션 데이터 삭제

### 5. 배포 및 마무리
- [ ] **CORS 설정** (Frontend <-> Backend 통신 허용)
- [ ] **예외 처리** (API 타임아웃, 생성 실패 시 기본 이미지 제공 등)