package gr.evansp.enums;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public enum Species {
	BULBASAUR,
	IVYSAUR;


	final int hp;
	final int attack;
	final int defence;
	final int specialAttack;
	final int specialDefence;
	final int speed;
	final String name;
	final List<Type> types;


	Species() {
		try(InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("pokedex.json")) {
			assert stream != null;
			String content = new String(stream.readAllBytes());
			JSONArray jsonArray = new JSONArray(content);

			JSONObject object = jsonArray.getJSONObject(this.ordinal());

			name = parseName(object);
			hp = parseBaseStat(object, "HP");
			attack = parseBaseStat(object, "Attack");
			defence = parseBaseStat(object, "Defense");
			specialAttack = parseBaseStat(object, "Sp. Attack");
			specialDefence = parseBaseStat(object, "Sp. Defense");
			speed = parseBaseStat(object, "Speed");
			types = parseTypes(object);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private int parseBaseStat(JSONObject object, String name) {
		JSONObject statObject = object.getJSONObject("base");
		return statObject.getInt(name);
	}

	private String parseName(JSONObject object) {
		JSONObject nameObject = object.getJSONObject("name");
		return nameObject.getString("english");
	}

	private List<Type> parseTypes(JSONObject object) {
		List<Type> types = new ArrayList<>();
		JSONArray jsonTypes = object.getJSONArray("type");

		for (int i=0; i < jsonTypes.length(); i++) {
			types.add(Type.valueOf(jsonTypes.getString(i).toUpperCase()));
		}
		return types;
	}
}
