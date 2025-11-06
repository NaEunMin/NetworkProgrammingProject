import java.io.Serializable;

/**
 * 서버-클라이언트 간 통신 메시지 정의
 */
public class NetworkProtocol {

    // --- C -> S (클라이언트가 서버로) ---

    /** (C -> S) 방 만들기 요청 */
    public record Msg_C2S_CreateRoom(String roomName, String password, int gameTimeSec, Team chosenTeam) implements Serializable {}

    /** (C -> S) 방 참여 요청 */
    public record Msg_C2S_JoinRoom(String roomName, String password) implements Serializable {}

    /** (C -> S) 게임 중 단어 입력 */
    public record Msg_C2S_InputRequest(Team team, String input) implements Serializable {}
    
    /** (C -> S) 게임방에서 나감 (게임 포기 또는 종료) */
    public record Msg_C2S_LeaveRoom() implements Serializable {}

    
    // --- S -> C (서버가 클라이언트로) ---

    /** (S -> C) 방 생성/참여 실패 응답 */
    public record Msg_S2C_RoomResponseFailure(String reason) implements Serializable {}

    /** (S -> C) 방 생성 성공 (아직 상대방 대기 중) */
    public record Msg_S2C_RoomCreatedWaiting(String roomName) implements Serializable {}
    
    /** (S -> C) 방 참여 성공 (양쪽 플레이어에게 전송되어 게임 시작) */
    public record Msg_S2C_GameStart(Team assignedTeam, Board board, int secondsLeft) implements Serializable {}

    /** (S -> C) 게임 중 단어 입력 처리 브로드캐스트 */
    public record Msg_S2C_BroadcastInput(Team team, String input) implements Serializable {}

    /** (S -> C) 게임 중 1초 경과 브로드캐스트 */
    public record Msg_S2C_Tick() implements Serializable {}
    
    /** (S -> C) 게임 종료 브로드캐스트 */
    public record Msg_S2C_GameOver() implements Serializable {}
    
    /** (S -> C) 상대방이 방을 나감 (게임 중단) */
    public record Msg_S2C_OpponentLeft() implements Serializable {}
    
    /** (S -> C) 로비로 복귀 (게임 종료 후) */
    public record Msg_S2C_ReturnToLobby() implements Serializable {}
}