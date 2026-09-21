# UCTale

<div align="center">
  <img src="./docs/images/project_logo.png" alt="UCTale" width="620" />
</div>

> **당신이 세계를 만들고, 선택하고, 그 선택으로 하나의 이야기를 만들어가는 인터랙티브 스토리 게임.**

UCTale은 정해진 시나리오를 따라가는 대신, 플레이어가 직접 세계와 주인공의 출발점을 만들고 선택을 이어가며 자신만의 이야기를 만들어가는 게임입니다.

[UCTale 플레이하기](https://uctale.vercel.app/)

---

## 당신의 이야기로 시작합니다

### 1. 세계와 주인공을 정합니다

<img src="./docs/images/main.png" alt="UCTale 이야기 시작 화면" width="900" />

어떤 세계인지, 그리고 그 안에서 당신이 누구인지만 정하면 이야기가 시작됩니다.

판타지 왕국의 용병이 될 수도 있고, 재난이 시작된 도시의 평범한 시민이 될 수도 있습니다. 출발점은 플레이어가 직접 만듭니다.

### 2. 현재 장면을 읽습니다

<img src="./docs/images/story.png" alt="UCTale 현재 이야기 화면" width="900" />

선택의 결과는 다음 장면으로 이어집니다. 상황과 분위기에 맞는 이야기와 장면 이미지가 함께 만들어집니다.

### 3. 다음 행동을 선택합니다

<img src="./docs/images/choice.png" alt="UCTale 선택지 화면" width="900" />

현재 상황에서 무엇을 할지 선택하세요. 같은 출발점에서도 어떤 행동을 고르느냐에 따라 이야기는 다른 방향으로 이어집니다.

---

## 선택이 쌓여 하나의 Tale이 됩니다

UCTale에서 중요한 것은 정답을 찾는 것이 아니라 **내가 어떤 선택을 했는지**입니다.

세계와 주인공을 만들고, 눈앞의 상황에 반응하고, 그 결과를 받아들이며 다음 선택으로 나아갑니다. 그렇게 이어진 장면들이 플레이어만의 이야기가 됩니다.

---

## 개발

백엔드:

```bash
./gradlew bootRun
```

프론트엔드:

```bash
cd frontend
npm ci
npm run dev
```

세부 개발 원칙과 프로젝트 문서는 [CONTRIBUTING.md](./CONTRIBUTING.md)와 [docs](./docs)를 참고하세요.
