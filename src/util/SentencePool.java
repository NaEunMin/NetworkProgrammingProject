package util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * 보너스 게임용 문장 공급기
 * 
 * [설계]
 * - 텍스트 파일(UTF-8)에서 문장 목록을 로드하여 메모리에 캐싱
 * - 보너스 타임 시작 시 무작위로 섞인 문장 리스트를 제공
 * - 파일 읽기 실패 시 기본 문장(Fallback) 제공하여 게임 중단 방지
 */
public class SentencePool {

    private final List<String> sentences;

    public SentencePool(List<String> sentences) {
        this.sentences = sentences;
    }

    /**
     * 지정된 개수만큼의 랜덤 문장 반환
     * (전체 목록을 셔플한 뒤 subList 반환)
     */
    public List<String> getRandomSentences(int count){
        if(sentences.isEmpty()){
            return Collections.emptyList();
        }
        List<String> shuffled = new ArrayList<>(sentences);
        Collections.shuffle(shuffled);
        return shuffled.subList(0,Math.min(count, shuffled.size()));
    }

    /**
     * 파일로부터 문장 풀 생성 (Factory Method)
     */
    public static SentencePool fromFile(String path){
        try{
            List<String> lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
            List<String> sentences = lines.stream()
            .map(String::trim).filter(s->!s.isEmpty()).toList();
            System.out.println("서버: "+ path + "에서 문장 " + sentences.size() + "개 불러옴.");
            return new SentencePool(sentences);
        } catch (IOException e){
            System.out.println("서버 : " + path + "파일 읽기 실패 : " + e.getMessage());
            // 실패 시 기본 문장 제공
            return new SentencePool(List.of("긴 문장 로딩에 실패했습니다.", "이것은 기본 문장입니다."));
        }
    }
}