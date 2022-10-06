import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Mensagem {
    public String messageType;
    public String sender;
    public String solicitanteInicial;
    public String fileName;

    public Mensagem(String type, String origem, String solicitante, String file) {
        messageType = type;
        sender = origem;
        solicitanteInicial = solicitante;
        fileName = file;
    }

    public Mensagem(String jsonString) {
        Pattern typePattern = Pattern.compile("\"messageType\": \"(.+?)\"");
        Pattern senderPattern = Pattern.compile("\"sender\": \"(.+?)\"");
        Pattern solicitanteInicialPattern = Pattern.compile("\"solicitanteInicial\": \"(.+?)\"");
        Pattern fileNamePattern = Pattern.compile("\"fileName\": \"(.+?)\"");

        Matcher typeMatcher = typePattern.matcher(jsonString);
        Matcher senderMatcher = senderPattern.matcher(jsonString);
        Matcher solicitanteInicialMatcher = solicitanteInicialPattern.matcher(jsonString);
        Matcher fileNameMatcher = fileNamePattern.matcher(jsonString);

        typeMatcher.find();
        senderMatcher.find();
        solicitanteInicialMatcher.find();
        fileNameMatcher.find();

        messageType = typeMatcher.group(1);
        sender = senderMatcher.group(1);
        solicitanteInicial = solicitanteInicialMatcher.group(1);
        fileName = fileNameMatcher.group(1);
    }

    public String toJson() {
        
        return String.format(
            "{\"messageType\": \"%s\", \"sender\": \"%s\", \"solicitanteInicial\": \"%s\", \"fileName\": \"%s\"}",
            messageType,
            sender,
            solicitanteInicial,
            fileName
        );
    }
}