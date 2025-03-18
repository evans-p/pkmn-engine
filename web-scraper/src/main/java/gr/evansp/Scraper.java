package gr.evansp;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Scraper for scraping pokemon names from <b>pokemonshowdown.com</b> and storing them to a file.
 * Next step is to use the data of the pokemon names, to scrape the pokemon's data.
 */
public class Scraper {
	public static void main(String[] args) throws InterruptedException {
		readPokemonNames();
	}

	/**
	 * Scrapes all pokemon names from <b>pokemonshowdown.com</b> and stores them to a file.
	 *
	 * @throws InterruptedException
	 * 		InterruptedException.
	 */
	private static void readPokemonNames() throws InterruptedException {
		List<String> pokemonNames = new ArrayList<>(2000);
		WebDriver driver = new ChromeDriver();
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

		driver.get("https://dex.pokemonshowdown.com/pokemon/");

		Thread.sleep(15000);

		List<WebElement> elements = driver.findElements(By.className("pokemonnamecol"));
		for (WebElement element : elements) {
			if (isValidName(element.getText())) {
				pokemonNames.add(element.getText());
			}
		}

		driver.quit();
		writeToFile(pokemonNames, "web-scraper/src/main/resources/pokemon-names.txt");
	}

	/**
	 * Filtering method, to remove invalid pokemon names. Filters following the rules below:
	 * <ul>
	 *     <li>If pokemon name contains the word 'mega', remove it</li>
	 *     <li>If pokemon name contains the word 'gmax', remove it</li>
	 *     <li>If pokemon name contains the word 'totem', remove it</li>
	 * </ul>
	 *
	 * @param name
	 * 		name
	 * @return if the name is valid or not.
	 */
	private static boolean isValidName(String name) {
		return !name.toLowerCase().contains("mega") &&
				       !name.toLowerCase().contains("gmax") &&
				       !name.toLowerCase().contains("totem");
	}

	/**
	 * Writes a list of strings to a file. Each string is written to a new line.
	 *
	 * @param names
	 * 		the list of strings
	 * @param fileName
	 * 		the file name
	 */
	private static void writeToFile(List<String> names, String fileName) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
			for (String item : names) {
				writer.write(item);
				writer.newLine();
			}
			System.out.println("List written to file successfully!");

		} catch (IOException e) {
			System.err.println("Error writing to file: " + e.getMessage());
		}
	}
}
