import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SentencePool {

    private final List<String> sentences;

    public SentencePool(List<String> sentences) {
        this.sentences = sentences;
    }

    public List<String> getRandomSentences(int count){
        if(sentences.isEmpty()){
            return Collections.emptyList();
        }
        List<String> shuffled = new ArrayList<>(sentences);
        Collections.shuffle(shuffled);
        return shuffled.subList(0,Math.min(count, shuffled.size()));
    }

    public static SentencePool fromFile(String path){
        try{
            List<String> lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
            List<String> sentences = lines.stream()
            .map(String::trim).filter(s->!s.isEmpty()).toList();
            System.out.println("서버: "+ path + "에서 문장 " + sentences.size() + "개 불러옴.");
            return new SentencePool(sentences);
        } catch (IOException e){
            System.out.println("서버 : " + path + "파일 읽기 실패 : " + e.getMessage());
            return new SentencePool(List.of("긴 문장 로딩에 실패했습니다.", "이것은 기본 문장입니다."));
        }
    }
}