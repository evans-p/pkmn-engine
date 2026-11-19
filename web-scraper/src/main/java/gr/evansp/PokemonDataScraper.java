package gr.evansp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Scraper for scraping the data of each pokemon (name, types, base stats, abilities, image) from
 * <b>dex.pokemonshowdown.com</b>. Reads the pokemon names produced by {@link PokemonNameScraper}, visits the page of
 * each pokemon, downloads its image and stores the collected data to a JSON file.
 */
public class PokemonDataScraper {

    private static final String BASE_URL = "https://dex.pokemonshowdown.com/pokemon/";
    private static final String NAMES_FILE = "web-scraper/src/main/resources/pokemon-names.txt";
    private static final String OUTPUT_FILE = "web-scraper/src/main/resources/pokemon-data.json";
    private static final String IMAGES_DIR = "web-scraper/src/main/resources/images";

    private static final Duration PAGE_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(15);

    // Selectors based on the markup built by the dex's pokemon panel (pokedex-pokemon.js).
    // The h1 also contains the dex number (<code>#116</code>), so only the link inside it is read.
    private static final By NAME = By.cssSelector(".dexentry h1 a");
    private static final By SPRITE = By.cssSelector(".dexentry img.sprite");
    private static final By TYPES = By.cssSelector("dl.typeentry a");
    private static final By ABILITIES = By.cssSelector("dl.abilityentry dd a");
    private static final By STAT_ROWS = By.cssSelector("table.stats tr");
    private static final By STAT_LABEL = By.tagName("th");
    private static final By STAT_VALUE = By.cssSelector("td.stat");

    /** Maps the stat labels shown on the page to short keys. */
    private static final Map<String, String> STAT_KEYS = Map.of(
            "HP", "hp",
            "Attack", "atk",
            "Defense", "def",
            "Sp. Atk", "spa",
            "Sp. Def", "spd",
            "Speed", "spe");

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /**
     * The data of a single pokemon.
     *
     * @param name
     *        the pokemon name
     * @param types
     *        the pokemon types
     * @param baseStats
     *        the base stats by key (hp, atk, def, spa, spd, spe)
     * @param abilities
     *        the pokemon abilities, including the hidden ability if it has one
     * @param image
     *        the file name of the downloaded image (inside the images directory), or null if the download
     *        failed
     */
    public record PokemonData(String name, List<String> types, Map<String, Integer> baseStats,
                              List<String> abilities, String image) {
    }

    public static void main(String[] args) throws IOException {
        List<String> ids = readIds(NAMES_FILE);
        System.out.println("Scraping " + ids.size() + " pokemon...");

        Path imagesDir = Path.of(IMAGES_DIR).toAbsolutePath();
        Files.createDirectories(imagesDir);
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(DOWNLOAD_TIMEOUT)
                .build();

        List<PokemonData> results = new ArrayList<>(ids.size());
        List<String> failed = new ArrayList<>();

        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("-headless");
        WebDriver driver = new FirefoxDriver(options);

        try {
            WebDriverWait wait = new WebDriverWait(driver, PAGE_TIMEOUT);
            int count = 0;
            for (String id : ids) {
                count++;
                try {
                    PokemonData data = scrapePokemon(driver, wait, httpClient, imagesDir, id);
                    results.add(data);
                    System.out.printf("[%d/%d] %s%n", count, ids.size(), data);
                } catch (RuntimeException e) {
                    failed.add(id);
                    System.err.printf("[%d/%d] Failed to scrape '%s': %s%n", count, ids.size(), id,
                            e.getClass().getSimpleName());
                }
            }
        } finally {
            driver.quit();
        }

        writeToJsonFile(results, OUTPUT_FILE);
        if (!failed.isEmpty()) {
            System.err.println("Failed pokemon (" + failed.size() + "): " + failed);
        }
    }

    /**
     * Visits the page of a single pokemon, scrapes its data and downloads its image.
     *
     * @param driver
     *        the web driver
     * @param wait
     *        the wait used to wait for the page to be rendered
     * @param httpClient
     *        the client used to download the image
     * @param imagesDir
     *        the directory the image is saved to
     * @param id
     *        the pokemon id, as used in the page url (e.g. <i>horsea</i>)
     * @return the pokemon data.
     */
    private static PokemonData scrapePokemon(WebDriver driver, WebDriverWait wait, HttpClient httpClient,
                                             Path imagesDir, String id) {
        driver.get(BASE_URL + id);
        // The panel builds the whole entry at once, so once the stats table exists everything else does too.
        wait.until(ExpectedConditions.presenceOfElementLocated(STAT_ROWS));

        String name = driver.findElement(NAME).getText().trim();
        List<String> types = texts(driver.findElements(TYPES));
        List<String> abilities = texts(driver.findElements(ABILITIES));
        Map<String, Integer> baseStats = readBaseStats(driver.findElements(STAT_ROWS));

        String imageUrl = driver.findElement(SPRITE).getAttribute("src");
        String image = downloadImage(httpClient, imageUrl, imagesDir, id);

        return new PokemonData(name, types, baseStats, abilities, image);
    }

    /**
     * Downloads an image and saves it as <i>id</i> plus the extension of the url (e.g. <i>horsea.png</i>).
     * A failed download does not fail the pokemon: it is logged and null is returned.
     *
     * @param httpClient
     *        the client
     * @param url
     *        the image url
     * @param imagesDir
     *        the directory to save the image to
     * @param id
     *        the pokemon id
     * @return the saved file name, or null if the download failed.
     */
    private static String downloadImage(HttpClient httpClient, String url, Path imagesDir, String id) {
        int dot = url.lastIndexOf('.');
        String extension = dot > url.lastIndexOf('/') ? url.substring(dot) : ".png";
        String fileName = id + extension;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(DOWNLOAD_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                System.err.printf("Image download for '%s' returned HTTP %d (%s)%n", id, response.statusCode(), url);
                return null;
            }
            Files.write(imagesDir.resolve(fileName), response.body());
            return fileName;
        } catch (IOException e) {
            System.err.printf("Image download for '%s' failed: %s%n", id, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Reads the base stats from the rows of the stats table. Rows without a stat value (the header row) and
     * rows that are not one of the six stats (the total) are skipped.
     *
     * @param rows
     *        the table rows
     * @return the base stats by key, in page order.
     */
    private static Map<String, Integer> readBaseStats(List<WebElement> rows) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (WebElement row : rows) {
            List<WebElement> values = row.findElements(STAT_VALUE);
            List<WebElement> labels = row.findElements(STAT_LABEL);
            if (values.isEmpty() || labels.isEmpty()) {
                continue;
            }
            String key = STAT_KEYS.get(labels.get(0).getText().replace(":", "").trim());
            Matcher matcher = NUMBER.matcher(values.get(0).getText());
            if (key != null && matcher.find()) {
                stats.put(key, Integer.parseInt(matcher.group()));
            }
        }
        return stats;
    }

    /**
     * Gets the non-empty trimmed texts of the elements.
     *
     * @param elements
     *        the elements
     * @return the texts.
     */
    private static List<String> texts(List<WebElement> elements) {
        List<String> texts = new ArrayList<>();
        for (WebElement element : elements) {
            String text = element.getText().trim();
            if (!text.isEmpty()) {
                texts.add(text);
            }
        }
        return texts;
    }

    /**
     * Reads the pokemon names from the file and converts them to the ids used in the page urls.
     *
     * @param fileName
     *        the file with one name per line
     * @return the distinct ids, in file order.
     * @throws IOException
     *        if the file cannot be read.
     */
    private static List<String> readIds(String fileName) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        for (String line : Files.readAllLines(Path.of(fileName))) {
            String id = toId(line);
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * Converts a name to a Showdown id: lowercase, letters and digits only (e.g. <i>Mr. Mime</i> becomes
     * <i>mrmime</i>).
     *
     * @param name
     *        the name
     * @return the id.
     */
    private static String toId(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Writes the pokemon data to a JSON file. Creates missing parent directories.
     *
     * @param data
     *        the pokemon data
     * @param fileName
     *        the file name
     */
    private static void writeToJsonFile(List<PokemonData> data, String fileName) {
        Path path = Path.of(fileName).toAbsolutePath();
        try {
            Files.createDirectories(path.getParent());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), data);
            System.out.println("Data written to file successfully: " + path);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
}