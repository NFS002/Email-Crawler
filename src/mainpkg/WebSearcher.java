package mainpkg;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by apple on 4/30/17.
 */
public class WebSearcher {

    private Pattern EMAIL_REGEXP = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}", Pattern.CASE_INSENSITIVE);

    private Pattern URL_REGEXP = Pattern.compile("http(s{0,1})://[a-zA-Z0-9_/\\-\\.]+\\.([A-Za-z/]{2,5})[a-zA-Z0-9_/\\&\\?\\=\\-\\.\\~\\%]*");

    private LinkedHashMap<String,ArrayList<String>> adressesToUrls = new LinkedHashMap<>();

    private ArrayList<String> subdomains = new ArrayList<>();

    private PrintWriter debugWriter = new PrintWriter(System.out);

    private long searchTimeLimit_seconds = 240;

    private SearchDepth searchDepth = SearchDepth.DEEP;

    private boolean regexSearch = false;

    private String outputDirName = "out";

    private long START_TIME = System.nanoTime();

    public void setSearchTimeLimit(long searchTimeLimit_seconds) {
        this.searchTimeLimit_seconds = searchTimeLimit_seconds;
    }

    public void setSearchDepth(SearchDepth searchDepth) {
        this.searchDepth = searchDepth;
    }

    public void setOutputDirName(String outputDirName) {
        this.outputDirName = outputDirName;
    }

    public void setRegexSearch(boolean regexSearch) {
        this.regexSearch = regexSearch;
    }

    public static void main(String[] args)  {
        WebSearcher main = new WebSearcher();
        if (args.length == 1) main.crawl(args[0]);
        else System.out.println("usage: java -cp \"lib/*\" mainpkg.WebSearcher <base_URL> [output_directory]");
    }

    public LinkedHashMap<String,ArrayList<String>> crawl(String url){
        createOutputDir(this.outputDirName);
        debugWriter.println("START");
        START_TIME = System.nanoTime();
        walk(url);
        saveAdresses(this.outputDirName);
        debugWriter.println("EXIT");
        debugWriter.close();
        return adressesToUrls;
    }

    private boolean isOneSubdomainOfTheOther(String url, String sub) {
        try {
            URL first = new URL(url);
            String firstHost = first.getHost();
            firstHost = firstHost.startsWith("www.") ? firstHost.substring(4) : firstHost;
            URL second = new URL(sub);
            String secondHost = second.getHost();
            secondHost = secondHost.startsWith("www.") ? secondHost.substring(4) : secondHost;
            /*
             Test if one is a substring of the other
             */
            if (firstHost.contains(secondHost) || secondHost.contains(firstHost)) {
                String[] firstPieces = firstHost.split("\\.");
                String[] secondPieces = secondHost.split("\\.");
                String[] longerHost = {""};
                String[] shorterHost = {""};

                if (firstPieces.length >= secondPieces.length) {
                    longerHost = firstPieces;
                    shorterHost = secondPieces;
                } else {
                    longerHost = secondPieces;
                    shorterHost = firstPieces;
                }
                //int longLength = longURL.length;
                int minLength = shorterHost.length;
                int i = 1;

                /*
                 Compare from the tail of both host and work backwards
                 */
                while (minLength > 0) {
                    String tail1 = longerHost[longerHost.length - i];
                    String tail2 = shorterHost[shorterHost.length - i];

                    if (tail1.equalsIgnoreCase(tail2)) {
                        //move up one place to the left
                        minLength--;
                    } else {
                        //domains do not match
                        return false;
                    }
                    i++;
                }
                if (minLength == 0) //shorter host exhausted. Is a sub domain
                    return true;
            }
        } catch (MalformedURLException ex) {
            debugWriter.println("ER: " + ex.getMessage() + "," + ex.getCause());
        }
        return false;
    }

    private void walk(String url){
        if (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - START_TIME) > searchTimeLimit_seconds) return;
        try {

            org.jsoup.nodes.Document d = Jsoup.connect(url).get();
            debugWriter.println("searching url: ");
            debugWriter.println(url);
            if (regexSearch) searchText(d);
            else searchTags(d);
        }
        catch (IOException e) {
            debugWriter.println("ER:" + e.getMessage() + ". Cannot connect:" + url);
        }
        debugWriter.flush();
        if (!subdomains.isEmpty()) walk(subdomains.remove(0));
    }

    private void searchTags(Document d){
        Elements elements = d.getElementsByAttribute("href");
        for (org.jsoup.nodes.Element e:elements) {
            //getEMAILS
            String emailStr = e.attr("href");
            if (emailStr.startsWith("mailto")) {
                emailStr = emailStr.replaceFirst("mailto:","");
                if (emailStr.startsWith("%")) emailStr = emailStr.replaceFirst("%\\d*","");
                if (emailStr.contains("subject=")) emailStr = emailStr.replaceAll("[&?]subject=[\\s\\S]+","");
                if (adressesToUrls.keySet().contains(emailStr)){
                    ArrayList<String> urlList = adressesToUrls.get(emailStr);
                    if (!urlList.contains(d.baseUri())) urlList.add(d.baseUri());
                }
                else {
                    ArrayList<String> urlList = new ArrayList<>();
                    urlList.add(d.baseUri());
                    adressesToUrls.put(emailStr,urlList);
                }
            }
            //getURLS
            String urlStr = e.attr("abs:href");
            if ((!urlStr.isEmpty()) && (!subdomains.contains(urlStr))) {
                switch(searchDepth) {
                    case CURRENT:
                        break;
                    case SUPER_DOMAIN:
                        if (isOneSubdomainOfTheOther(d.baseUri(),urlStr)) subdomains.add(urlStr);
                        break;
                    case DEEP:
                        subdomains.add(urlStr);
                        break;
                }
            }
        }
    }

    private void searchText(Document d){
        Matcher emailMatcher = EMAIL_REGEXP.matcher(d.html());
        while (emailMatcher.find()){
            if (adressesToUrls.keySet().contains(emailMatcher.group())){
                ArrayList<String> urlList = adressesToUrls.get(emailMatcher.group());
                if (!urlList.contains(d.baseUri())) urlList.add(d.baseUri());
            }
            else {
                ArrayList<String> urlList = new ArrayList<>();
                urlList.add(d.baseUri());
                adressesToUrls.put(emailMatcher.group(),urlList);
            }
        }
        Matcher urlMatcher = URL_REGEXP.matcher(d.html());
        while (urlMatcher.find()){
            if (!subdomains.contains(emailMatcher.group())) {
                switch(searchDepth) {
                    case CURRENT:
                        break;
                    case SUPER_DOMAIN:
                        if (isOneSubdomainOfTheOther(d.baseUri(),emailMatcher.group())) subdomains.add(emailMatcher.group());
                        break;
                    case DEEP:
                        subdomains.add(emailMatcher.group());
                        break;
                }
            }
        }
    }

    private void createOutputDir(String outputDir) {
        File out = new File(outputDir);
        if (!out.exists()) out.mkdir();
        File debug = new File(out.getAbsolutePath() + "/debug");
        if (!debug.exists()) debug.mkdir();
        try {
        debugWriter = new PrintWriter(debug.getAbsolutePath() + "/log.txt");
        }
        catch (FileNotFoundException fe) {
            System.out.println("ERROR:" + fe.getMessage());
        }
    }

    private void saveAdresses(String outputDir){
        try {
            File dir = new File(outputDir);
            if (!dir.exists()) dir.mkdir();
            String path = dir.getAbsolutePath();
            dir = new File(path + "/adresses");
            if (!dir.exists()) {
                dir.mkdir();
                PrintWriter writer = new PrintWriter(dir.getAbsolutePath() + "/part-0000.txt");
                for (Map.Entry<String,ArrayList<String>> entry: adressesToUrls.entrySet()) {
                    writer.print("Email Address: " + entry.getKey() + "  - ");
                    writer.println("Found At: " + entry.getValue());
                }
                writer.close();
            }
            else {
                debugWriter.println("ERROR:Output directory already in use");
            }
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
