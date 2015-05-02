package utilities.namegen;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import resources.Race;
import resources.zone.NameFilter;

public class SWGNameGenerator {

    private Map<String, RaceNameRule> ruleMap = new HashMap<>();
    
    private NameFilter nameFilter;
    private String race;
    
    /**
     * Creates a new instance of {@link SWGNameGenerator} with all racial naming rules loaded.
     */
	public SWGNameGenerator(NameFilter nameFilter) {
		this.nameFilter = nameFilter;
	}
	
	/**
	 * Creates a new instance of {@link SWGNameGenerator} loaded with only the naming rules for the defined race. If a name is attempted to be generated 
	 * later that is not this race, then it'll load the naming rules specific to that race before generating a name.
	 * @param race Race to load the naming rules for.
	 */
	public SWGNameGenerator(Race race) {
		loadNamingRule(race.getSpecies());
		this.race = race.getSpecies().split("_")[0];
	}
	
	public void loadAllRules() {
		loadNamingRule(Race.RODIAN_MALE.getSpecies());
		loadNamingRule(Race.BOTHAN_MALE.getSpecies());
		loadNamingRule(Race.HUMAN_MALE.getSpecies());
		loadNamingRule(Race.ITHORIAN_MALE.getSpecies());
		loadNamingRule(Race.MONCAL_MALE.getSpecies());
		loadNamingRule(Race.SULLUSTAN_MALE.getSpecies());
		loadNamingRule(Race.TWILEK_MALE.getSpecies());
		loadNamingRule(Race.WOOKIEE_MALE.getSpecies());
		loadNamingRule(Race.ZABRAK_MALE.getSpecies());
	}

	/**
	 * Creates a new instance of {@link SWGNameGenerator} loaded with only the naming rules for the defined race. If a name is attempted to be generated
	 * later that is not this race, then it'll load the naming rules specific to that race before generating a name.
	 * @param race Race to load the naming rules for.
	 */
	public SWGNameGenerator(String race) {
		loadNamingRule(race);
		this.race = race;
	}
	
	/**
	 * Generates a random name for the species the generator was initialized with. If initialized without a {@link Race}, the species is random.
	 * This will also generate a surname for the race depending on the surname chance.
	 * @return Generated name in the form of a {@link String}, including surname dependent on chance.
	 */
	public String generateRandomName() {
		if (race == null || race.isEmpty()) {
			int i = randomInt(1, 10);
			
			switch (i) {
			
			case 1: return generateRandomName("rodian");
			case 2: return generateRandomName("sullustan");
			case 3: return generateRandomName("trandoshan");
			case 4: return generateRandomName("human");
			case 5: return generateRandomName("bothan");
			case 6: return generateRandomName("wookiee");
			case 7: return generateRandomName("twilek");
			case 8: return generateRandomName("zabrak");
			case 9: return generateRandomName("moncal");
			case 10: return generateRandomName("ithorian");
			
			}
			
		}

		return generateRandomName(race);
	}
	
	/**
	 * Generates a random name for the defined race.
	 * @param race Species to generate a name for
	 * @param surname Determines if a surname should be generated or not.
	 * @return Generated name in the form of a {@link String}, as well as the surname dependent on chance if true
	 */
	public String generateRandomName(String race, boolean surname) {
		if (!ruleMap.containsKey(race) && !loadNamingRule(race))
			return "";
		
		String name = null;
		while (name == null || !nameFilter.isValid(name)) {
			name = getNameByRule(ruleMap.get(race));
			// Some reason a name can be empty, I think it has to do with the removeExcessDuplications check.
			if (name.isEmpty())
				name = getNameByRule(ruleMap.get(race));
			
			if (surname)
				name += (shouldGenerateSurname(ruleMap.get(race)) ? " " + generateRandomName(race, false) : "");
			
			name = firstCharUppercase(name);
		}
		
		return name;
	}
	
	/**
	 * Generates a random name and surname for the defined race depending on the chance specific to this race.
	 * @param race Species to generate a name for.
	 * @return Generated name in the form of a{@link String}, including surname dependent on chance.
	 */
	public String generateRandomName(String race) {
		return generateRandomName(race, true);
	}
	
	/**
	 * Generates a random name specific to the {@link Race} given. This will include a surname depending on the chance specific to this race.
	 * @param race Race/Species to generate a name for.
	 * @return Generated name in the form of a {@link String}.
	 */
	public String generateRandomName(Race race){
		return generateRandomName(race.getSpecies());
	}
	
	private String getNameByRule(RaceNameRule rule) {
		String name = "";
		String instructions = getRandomInstruction(rule);
		int l = instructions.length();

		for (int i = 0; i < l; i++) { 
			char x = instructions.charAt(0); 
			
			switch (x) { 
			case 'v': name += removeExcessDuplications(rule.getVowels(), name, getRandomElementFrom(rule.getVowels())); 
				break; 
			case 'c': name += removeExcessDuplications(rule.getStartConsonants(), name, getRandomElementFrom(rule.getStartConsonants())); 
				break; 
			case 'd': name += removeExcessDuplications(rule.getEndConsonants(), name, getRandomElementFrom(rule.getEndConsonants())); 
				break;
			case '/': name += "'";
				break;
			}
			
			instructions = instructions.substring(1); 
		}
		if (name.isEmpty())
			return getNameByRule(rule);
		return name; 
	} 
	
	private String getRandomInstruction(RaceNameRule rule) {
		return getRandomElementFrom(rule.getInstructions());
	}
	
	private boolean loadNamingRule(String race) {
		String species = race.split("_")[0];
		
		RaceNameRule rule = null;
		try {
			FileInputStream fileStream = new FileInputStream("./namegen/" + species + ".txt");
			rule = createRaceRule(fileStream);
			fileStream.close();
			if (rule != null)
				ruleMap.put(species, rule);
			
			return (rule != null);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return (rule != null);
	}
	
	private RaceNameRule createRaceRule(FileInputStream stream) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		RaceNameRule rule = new RaceNameRule();
		
		boolean success = false;
		try {
			success = populateRule(rule, reader);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return (success ? rule : null);
	}
	
	private boolean populateRule(RaceNameRule rule, BufferedReader reader) throws IOException {

		String populating = "NONE";
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.isEmpty())
				continue;
			
			if (line.startsWith("[")) {
				populating = getPopulateType(line);
				
				if (populating.equals("End"))
					return true;
				
			} else if (!line.startsWith("#")) {
				switch (populating) {
				case "Settings":
					populateSettings(rule, line);
					break;
				case "Vowels": 
					rule.addVowel(line);
					break;
				case "StartConsonants": 
					rule.addStartConsonant(line);
					break;
				case "EndConsonants": 
					rule.addEndConsant(line);
					break;
				case "Instructions": 
					rule.addInstruction(line);
					break;
				}
			}
		}
		reader.close();
		
		return false;
	}
	
	private String getPopulateType(String line) {
		switch (line) {
		case "[Settings]":
			return "Settings";
		case "[Vowels]": 
			return "Vowels";
		case "[StartConsonants]": 
			return "StartConsonants";
		case "[EndConsonants]": 
			return "EndConsonants";
		case "[Instructions]": 
			return "Instructions";
		case "[END]": 
			return "End";
		default: return "End";
		}
	}
	
	private void populateSettings(RaceNameRule rule, String line) {
		String key = line.split("=")[0];
		String value = line.split("=")[1];
		
		switch (key) {
		case "SurnameChance":
			rule.setSurnameChance(Integer.valueOf(value));
			break;
		default: return;
		}
	}
	
	private String firstCharUppercase(String name) { 
		return Character.toString(name.charAt(0)).toUpperCase() + name.substring(1); 
	} 
	
	private String removeExcessDuplications(List<String> list, String orig, String n) {
		// Only checks the first and last for repeating
		if (orig.length() <= 1)
			return n;
		
		if (orig.charAt(orig.length() - 1) == n.charAt(0)) {
			if (!list.contains(Character.toString(orig.charAt(orig.length() - 1)) + Character.toString(n.charAt(0)))) {
				return removeExcessDuplications(list, orig, getRandomElementFrom(list));
			}
		}
		return n;
	}
	
	private String getRandomElementFrom(List<String> list) {
		return list.get(randomInt(0, list.size() - 1));
	}
	
	private int randomInt(int min, int max) {
		return (int) (min + (Math.random() * (max + 1 - min)));
	}
	
	private boolean shouldGenerateSurname(RaceNameRule rule) {
		if (rule.getSurnameChance() == 0)
			return false;
		
		return (randomInt(0,100) <= rule.getSurnameChance());
	}
}
