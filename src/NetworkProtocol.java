import java.io.Serializable;
import java.util.List;

/**
 * 서버-클라이언트 간 통신 메시지 정의
 */
public class NetworkProtocol {

    // --- 공통 구조 ---
    public record RoomInfo(String name, int seconds, int currentPlayers, int maxPlayers, boolean playing) implements Serializable {}
    public record PlayerInfo(String nickname, Team team, boolean ready, boolean owner) implements Serializable {}

    // --- C -> S (클라이언트 -> 서버) ---

    /** 첫 연결 후 닉네임 전달 */
    public record Msg_C2S_Handshake(String nickname) implements Serializable {}

    /** 방 만들기 요청 */
    public record Msg_C2S_CreateRoom(String roomName, String password, int gameTimeSec, Team chosenTeam) implements Serializable {}

    /** 방 참여 요청 */
    public record Msg_C2S_JoinRoom(String roomName, String password) implements Serializable {}

    /** 로비 룸 목록 요청 */
    public record Msg_C2S_RequestRoomList() implements Serializable {}

    /** 게임 내 입력 */
    public record Msg_C2S_InputRequest(Team team, String input) implements Serializable {}
    
    /** 게임방에서 나가기 (게임 도중 종료) */
    public record Msg_C2S_LeaveRoom() implements Serializable {}

    /** 대기방에서 게임 시작 (방장만) */
    public record Msg_C2S_StartGame() implements Serializable {}

    /** 대기방 준비 상태 변경 */
    public record Msg_C2S_ToggleReady(boolean ready) implements Serializable {}

    /** 대기방 채팅 */
    public record Msg_C2S_WaitingChat(String text) implements Serializable {}

    
    // --- S -> C (서버 -> 클라이언트) ---

    /** 현재 로비 룸 목록 전체 */
    public record Msg_S2C_RoomList(List<RoomInfo> rooms) implements Serializable {}

    /** 특정 룸 정보 갱신 */
    public record Msg_S2C_RoomUpdated(RoomInfo room) implements Serializable {}

    /** 룸이 사라짐 */
    public record Msg_S2C_RoomRemoved(String roomName) implements Serializable {}

    /** 방 생성/참여 실패 응답 */
    public record Msg_S2C_RoomResponseFailure(String reason) implements Serializable {}

    /** 대기방 입장 정보 */
    public record Msg_S2C_EnterWaitingRoom(RoomInfo room, List<PlayerInfo> players, Team myTeam) implements Serializable {}

    /** 대기방 플레이어 리스트 갱신 */
    public record Msg_S2C_PlayerListUpdated(List<PlayerInfo> players) implements Serializable {}

    /** 대기방 채팅 브로드캐스트 */
    public record Msg_S2C_WaitingChat(String sender, String text) implements Serializable {}
    
    /** 게임 시작 */
    public record Msg_S2C_GameStart(Team assignedTeam, Board board, int secondsLeft) implements Serializable {}

    /** 게임 입력 처리 브로드캐스트 */
    public record Msg_S2C_BroadcastInput(Team team, String input) implements Serializable {}

    /** 게임 1초 경과 브로드캐스트 */
    public record Msg_S2C_Tick() implements Serializable {}
    
    /** 게임 종료 브로드캐스트 */
    public record Msg_S2C_GameOver() implements Serializable {}
    
    /** 상대방이 방을 떠남 (게임 중단) */
    public record Msg_S2C_OpponentLeft() implements Serializable {}
    
    /** 로비 복귀 (게임 종료 등) */
    public record Msg_S2C_ReturnToLobby() implements Serializable {}

    // -- 보너스 타임 관련 메시지 --
    /** 보너스 타임 시작 */
    public record Msg_S2C_BonusTimeStart(List<String> sentences) implements Serializable {}

    /** (클라이언트 -> 서버) 문장 입력 */
    public record Msg_C2S_SentenceInput(String sentence, Team team) implements Serializable {}

    /** 문장 입력 결과 */
    public record Msg_S2C_BonusSentenceResult(boolean success, String sentence, Team team) implements Serializable {
    }

    /** 보너스 타임 종료 */
    public record Msg_S2C_BonusTimeEnd() implements Serializable {}

}
