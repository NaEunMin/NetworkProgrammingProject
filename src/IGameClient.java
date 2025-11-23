
/**
 * 게임 프레임(GameFrame)이 서버(또는 로컬 게임 매니저)와 통신하기 위한 인터페이스.
 * 멀티플레이(GameClient)와 싱글플레이(SingleGameManager)가 이를 구현한다.
 */
public interface IGameClient {
    /** 게임 내 입력 요청 (단어 뒤집기) */
    void sendInputRequest(Team team, String input);

    /** 보너스 타임 문장 입력 요청 */
    void sendSentenceInput(Team team, String sentence);

    /** 게임이 정상적으로 종료되었음을 알림 (UI -> 로직) */
    void gameHasFinished();

    /** 게임 도중 연결 끊기/나가기 요청 */
    void disconnectFromGame();
}
