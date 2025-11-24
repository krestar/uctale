# UCTale Frontend

UCTale(User Create Tale)의 프론트엔드 프로젝트입니다.
React와 Vite를 기반으로 구축되었으며, 사용자와 AI 간의 상호작용을 위한 직관적인 채팅형 UI와 동적 이미지 렌더링을 담당합니다.

## 🛠 기술 스택
- **Core:** React 18, Vite
- **Language:** JavaScript (ES6+)
- **Styling:** CSS Modules, Responsive Design (Mobile/PC)
- **HTTP Client:** Axios
- **Deployment:** Vercel

## 🚀 실행 방법

### 1. 사전 요구사항
- Node.js 18.0.0 이상
- npm 또는 yarn

### 2. 의존성 설치
```bash
npm install
```

### 3. 개발 서버 실행
```bash
npm run dev
```
실행 후 터미널에 표시되는 로컬 주소(예: http://localhost:5173)로 접속하여 테스트할 수 있습니다.

### 4. 프로덕션 빌드
```
npm run build
```
빌드된 파일은 dist 폴더에 생성됩니다.

## 📂 주요 디렉토리 구조
```bash
src/
├── api/            # 백엔드 API 통신 로직
├── components/     # 재사용 가능한 UI 컴포넌트 (GameImage, TypewriterText 등)
├── App.jsx         # 메인 게임 로직 및 상태 관리
└── App.css         # 반응형 스타일링 및 테마 정의
```
