package com.laits.breeding;

import com.laits.breeding.models.AnimalType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AnimalType enum.
 */
class AnimalTypeTest {

    @Test
    void testCowProperties() {
        AnimalType cow = AnimalType.COW;

        assertEquals("cow", cow.getId());
        assertEquals("wheat", cow.getBreedingFood());
        assertTrue(cow.isBreedingFood("wheat"));
        assertTrue(cow.isBreedingFood("WHEAT"));
        assertTrue(cow.isBreedingFood("hytale:wheat"));
        assertFalse(cow.isBreedingFood("carrot"));
    }

    @Test
    void testPigProperties() {
        AnimalType pig = AnimalType.PIG;

        assertEquals("pig", pig.getId());
        assertEquals("carrot", pig.getBreedingFood());
        assertTrue(pig.isBreedingFood("carrot"));
        assertTrue(pig.isBreedingFood("golden_carrot")); // Contains "carrot"
        assertFalse(pig.isBreedingFood("wheat"));
    }

    @Test
    void testChickenProperties() {
        AnimalType chicken = AnimalType.CHICKEN;

        assertEquals("chicken", chicken.getId());
        assertEquals("seeds", chicken.getBreedingFood());
        assertTrue(chicken.isBreedingFood("seeds"));
        assertTrue(chicken.isBreedingFood("wheat_seeds"));
        assertTrue(chicken.isBreedingFood("pumpkin_seeds"));
        assertFalse(chicken.isBreedingFood("wheat"));
    }

    @Test
    void testSheepProperties() {
        AnimalType sheep = AnimalType.SHEEP;

        assertEquals("sheep", sheep.getId());
        assertEquals("wheat", sheep.getBreedingFood());
        assertTrue(sheep.isBreedingFood("wheat"));
        assertFalse(sheep.isBreedingFood("carrot"));
    }

    @Test
    void testFromEntityTypeId() {
        assertEquals(AnimalType.COW, AnimalType.fromEntityTypeId("cow"));
        assertEquals(AnimalType.COW, AnimalType.fromEntityTypeId("hytale:cow"));
        assertEquals(AnimalType.COW, AnimalType.fromEntityTypeId("COW"));

        assertEquals(AnimalType.PIG, AnimalType.fromEntityTypeId("pig"));
        assertEquals(AnimalType.CHICKEN, AnimalType.fromEntityTypeId("chicken"));
        assertEquals(AnimalType.SHEEP, AnimalType.fromEntityTypeId("sheep"));

        assertNull(AnimalType.fromEntityTypeId("wolf"));
        assertNull(AnimalType.fromEntityTypeId("zombie"));
        assertNull(AnimalType.fromEntityTypeId(null));
    }

    @Test
    void testIsBreedingFoodWithNull() {
        assertFalse(AnimalType.COW.isBreedingFood(null));
        assertFalse(AnimalType.PIG.isBreedingFood(null));
    }

    @Test
    void testAllAnimalTypesHaveRequiredProperties() {
        for (AnimalType type : AnimalType.values()) {
            assertNotNull(type.getId(), "Animal type should have ID");
            assertNotNull(type.getBreedingFood(), "Animal type should have breeding food");
            assertFalse(type.getId().isEmpty(), "Animal ID should not be empty");
            assertFalse(type.getBreedingFood().isEmpty(), "Breeding food should not be empty");
        }
    }
}
