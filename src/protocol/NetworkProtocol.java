package protocol;
import java.io.Serializable;
import java.util.List;

import game.Board;
import game.Pos;
import game.Team;

/**
 * 서버-클라이언트 간 통신 프로토콜 정의
 * 
 * [설계]
 * - Java record를 사용하여 불변 메시지 객체 구현
 * - Serializable 구현으로 ObjectInputStream/ObjectOutputStream을 통한 전송 가능
 * - 명명 규칙: Msg_C2S_(메시지명) - 클라이언트→서버, Msg_S2C_(메시지명) - 서버→클라이언트
 * 
 * [통신 흐름]
 * 1. 연결: Handshake
 * 2. 로비: RoomList, CreateRoom, JoinRoom
 * 3. 대기실: EnterWaitingRoom, PlayerListUpdated, ToggleReady, WaitingChat
 * 4. 게임: GameStart, InputRequest, BroadcastInput, Tick, GameOver
 * 5. 보너스: BonusTimeStart, SentenceInput, BonusSentenceResult, BonusTimeEnd
 */
public class NetworkProtocol {

        /**
         * 게임 테마 (UI 색상 및 리소스 결정)
         */
        public enum Theme {
                PIRATE_LAIR("해적의 소굴"),
                NIGHT_MARKET("해적의 야시장");

                private final String label;

                Theme(String label) {
                        this.label = label;
                }

                public String getLabel() {
                        return label;
                }
        }

        // ===== 공통 데이터 구조 =====

        /**
         * 방 정보 (로비 및 대기실에서 사용)
         */
        public record RoomInfo(String name, int seconds, int totalSeconds, boolean bonusEnabled, Theme theme,
                        int currentPlayers, int maxPlayers, boolean playing)
                        implements Serializable {
        }

        /**
         * 플레이어 정보 (대기실에서 사용)
         */
        public record PlayerInfo(String nickname, Team team, boolean ready, boolean owner) implements Serializable {
        }

        // ===== 클라이언트 → 서버 메시지 =====

        /** 첫 연결 시 닉네임 전달 */
        public record Msg_C2S_Handshake(String nickname) implements Serializable {
        }

        /** 방 생성 요청 */
        public record Msg_C2S_CreateRoom(String roomName, String password, int gameTimeSec, boolean bonusEnabled,
                        Theme theme, Team chosenTeam)
                        implements Serializable {
        }

        /** 방 참여 요청 */
        public record Msg_C2S_JoinRoom(String roomName, String password) implements Serializable {
        }

        /** 로비에서 방 목록 요청 */
        public record Msg_C2S_RequestRoomList() implements Serializable {
        }

        /** 게임 중 단어 입력 */
        public record Msg_C2S_InputRequest(Team team, String input) implements Serializable {
        }

        /** 방 나가기 (게임 중단) */
        public record Msg_C2S_LeaveRoom() implements Serializable {
        }

        /** 게임 시작 요청 (방장만 가능) */
        public record Msg_C2S_StartGame() implements Serializable {
        }

        /** 준비 상태 토글 (대기실) */
        public record Msg_C2S_ToggleReady(boolean ready) implements Serializable {
        }

        /** 대기실 채팅 메시지 전송 */
        public record Msg_C2S_WaitingChat(String text) implements Serializable {
        }

        /** 보너스 타임 문장 입력 */
        public record Msg_C2S_SentenceInput(String sentence, Team team) implements Serializable {
        }

        // ===== 서버 → 클라이언트 메시지 =====

        /** 대기실 입장 성공 (방 정보 + 플레이어 목록 + 배정된 팀) */
        public record Msg_S2C_EnterWaitingRoom(RoomInfo room, List<PlayerInfo> players, Team myTeam)
                        implements Serializable {
        }

        /** 대기실 플레이어 목록 갱신 (누군가 들어오거나 나갈 때) */
        public record Msg_S2C_PlayerListUpdated(List<PlayerInfo> players) implements Serializable {
        }

        /** 게임 시작 (초기 보드 상태 전송) */
        public record Msg_S2C_GameStart(Team assignedTeam, Board board, int secondsLeft, Theme theme)
                        implements Serializable {
        }

        /**
         * 입력 처리 결과 브로드캐스트
         * 클라이언트는 이 메시지를 받아 GameModel.flipByInput() 호출
         */
        public record Msg_S2C_BroadcastInput(Team team, String input) implements Serializable {
        }

        /** 1초 경과 알림 (타이머 동기화) */
        public record Msg_S2C_Tick() implements Serializable {
        }

        /** 게임 종료 알림 */
        public record Msg_S2C_GameOver() implements Serializable {
        }

        /** 상대방이 게임 중 나감 (게임 중단) */
        public record Msg_S2C_OpponentLeft() implements Serializable {
        }

        /** 로비로 복귀 (게임 종료 또는 중단 후) */
        public record Msg_S2C_ReturnToLobby() implements Serializable {
        }

        /** 보너스 타임 시작 (문장 목록 전달) */
        public record Msg_S2C_BonusTimeStart(List<String> sentences) implements Serializable {
        }

        /** 보너스 타임 문장 입력 결과 (성공 여부 + 문장 + 입력한 팀) */
        public record Msg_S2C_BonusSentenceResult(boolean success, String sentence, Team team) implements Serializable {
        }

        /** 보너스 타임 종료 */
        public record Msg_S2C_BonusTimeEnd() implements Serializable {
        }

        /** 방 생성/참여 실패 응답 (실패 이유 포함) */
        public record Msg_S2C_RoomResponseFailure(String reason) implements Serializable {
        }

        /** 로비 방 목록 응답 */
        public record Msg_S2C_RoomList(List<RoomInfo> rooms) implements Serializable {
        }

        /** 방 정보 갱신 (로비에 표시 중인 방의 상태가 변경됨) */
        public record Msg_S2C_RoomUpdated(RoomInfo room) implements Serializable {
        }

        /** 방 삭제 알림 (로비에서 방 제거) */
        public record Msg_S2C_RoomRemoved(String roomName) implements Serializable {
        }

        /** 대기실 채팅 메시지 수신 */
        public record Msg_S2C_WaitingChat(String sender, String text) implements Serializable {
        }

        /**
         * 특정 셀 강제 업데이트 (서버에서 SPECIAL 칸 생성 시 사용)
         * Board 전체를 보내지 않고 변경된 셀만 전송하여 네트워크 효율성 향상
         */
        public record Msg_S2C_CellUpdate(Pos pos, Team owner, String token) implements Serializable {
        }

}
