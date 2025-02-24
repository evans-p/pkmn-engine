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


public class Scraper {
	public static void main(String[] args) throws InterruptedException {
		readPokemonNames();
	}

	private static void readPokemonNames() throws InterruptedException {
		List<String> pokemonnames = new ArrayList<>(2000);
		WebDriver driver = new ChromeDriver();
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

		driver.get("https://dex.pokemonshowdown.com/pokemon/");

		Thread.sleep(15000);

		List<WebElement> elements = driver.findElements(By.className("pokemonnamecol"));
		for (WebElement element : elements) {
			if (isValidName(element.getText())) {
				pokemonnames.add(element.getText());
			}
		}

		driver.quit();

		writeToFile(pokemonnames);
	}


	private static boolean isValidName(String name) {
		return !name.toLowerCase().contains("mega") &&
		       !name.toLowerCase().contains("gmax") &&
		       !name.toLowerCase().contains("totem");
	}

	private static void writeToFile(List<String> names) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter("web-scraper/src/main/resources/pokemon-names.txt"))) {
			for (String item : names) {
				writer.write(item);
				writer.newLine(); // Add a new line after each item
			}
			System.out.println("List written to file successfully!");

		} catch (IOException e) {
			System.err.println("Error writing to file: " + e.getMessage());
		}
	}
}
