package gr.evansp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

/**
 * Scraper for scraping pokemon names from <b>pokemonshowdown.com</b> and storing them to a JSON file.
 * Next step is to use the data of the pokemon names, to scrape the pokemon's data.
 */
public class Scraper {
    public static void main(String[] args) throws InterruptedException {
        readPokemonNames();
    }

    /**
     * Scrapes all pokemon names from <b>pokemonshowdown.com</b>, normalizes them and stores them to a JSON file.
     *
     * @throws InterruptedException InterruptedException.
     */
    private static void readPokemonNames() throws InterruptedException {
        List<String> pokemonNames = new ArrayList<>(2000);
        WebDriver driver = new FirefoxDriver();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        driver.get("https://dex.pokemonshowdown.com/pokemon/");

        Thread.sleep(15000);

        List<WebElement> elements = driver.findElements(By.className("pokemonnamecol"));
        for (WebElement element : elements) {
            String name = normalizeName(element.getText());
            if (isValidName(name)) {
                pokemonNames.add(name);
            }
        }

        driver.quit();
        writeToFile(pokemonNames, "web-scraper/src/main/resources/pokemon-names.txt");
    }

    /**
     * Normalizes a pokemon name: converts it to lowercase, removes dashes and trims whitespace.
     *
     * @param name the raw name
     * @return the normalized name.
     */
    private static String normalizeName(String name) {
        return name.toLowerCase()
				.replace("-", "")
				.trim()
				.replace(" ", "")
				.replace(".", "");
    }

    /**
     * Filtering method, to remove invalid pokemon names. Expects an already normalized name.
     * Filters following the rules below:
     * <ul>
     *     <li>If pokemon name contains the word 'mega', remove it</li>
     *     <li>If pokemon name contains the word 'gmax', remove it</li>
     *     <li>If pokemon name contains the word 'totem', remove it</li>
     * </ul>
     *
     * @param name normalized name
     * @return if the name is valid or not.
     */
    private static boolean isValidName(String name) {
        return !name.isEmpty() && !name.contains("mega") && !name.contains("gmax") && !name.contains("totem");
    }

    /**
     * Writes a list of strings to a JSON file, as a JSON array.
     *
     * @param names    the list of strings
     * @param fileName the file name
     */
    private static void writeToFile(List<String> names, String fileName) {
        Path path = Path.of(fileName).toAbsolutePath();
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, names);
            System.out.println("List written to file successfully: " + path);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
}