package com.laits.breeding;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.GrowthStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AnimalType enum.
 */
class AnimalTypeTest {

    @Nested
    @DisplayName("Livestock animals")
    class LivestockAnimals {

        @Test
        @DisplayName("COW should have correct properties")
        void testCowProperties() {
            AnimalType cow = AnimalType.COW;

            assertThat(cow.getId()).isEqualTo("cow");
            assertThat(cow.getModelAssetId()).isEqualTo("Cow");
            assertThat(cow.getBreedingFood()).isEqualTo("Plant_Crop_Cauliflower_Item");
            assertThat(cow.getBabyNpcRoleId()).isEqualTo("Cow_Calf");
            assertThat(cow.isLivestock()).isTrue();
            assertThat(cow.hasBabyVariant()).isTrue();
        }

        @Test
        @DisplayName("PIG should have correct properties")
        void testPigProperties() {
            AnimalType pig = AnimalType.PIG;

            assertThat(pig.getId()).isEqualTo("pig");
            assertThat(pig.getBreedingFood()).isEqualTo("Plant_Crop_Mushroom_Cap_Brown");
            assertThat(pig.getBabyNpcRoleId()).isEqualTo("Pig_Piglet");
            assertThat(pig.isLivestock()).isTrue();
        }

        @Test
        @DisplayName("CHICKEN should have correct properties")
        void testChickenProperties() {
            AnimalType chicken = AnimalType.CHICKEN;

            assertThat(chicken.getId()).isEqualTo("chicken");
            assertThat(chicken.getBreedingFood()).isEqualTo("Plant_Crop_Corn_Item");
            assertThat(chicken.getBabyNpcRoleId()).isEqualTo("Chicken_Chick");
        }

        @Test
        @DisplayName("SHEEP should have correct properties")
        void testSheepProperties() {
            AnimalType sheep = AnimalType.SHEEP;

            assertThat(sheep.getId()).isEqualTo("sheep");
            assertThat(sheep.getBreedingFood()).isEqualTo("Plant_Crop_Lettuce_Item");
            assertThat(sheep.getBabyNpcRoleId()).isEqualTo("Sheep_Lamb");
        }
    }

    @Nested
    @DisplayName("fromEntityTypeId()")
    class FromEntityTypeId {

        @Test
        @DisplayName("should match exact model asset ID")
        void shouldMatchExact() {
            assertThat(AnimalType.fromEntityTypeId("Cow")).isEqualTo(AnimalType.COW);
            assertThat(AnimalType.fromEntityTypeId("Pig")).isEqualTo(AnimalType.PIG);
            assertThat(AnimalType.fromEntityTypeId("Chicken")).isEqualTo(AnimalType.CHICKEN);
            assertThat(AnimalType.fromEntityTypeId("Sheep")).isEqualTo(AnimalType.SHEEP);
        }

        @Test
        @DisplayName("should match case-insensitively")
        void shouldMatchCaseInsensitive() {
            assertThat(AnimalType.fromEntityTypeId("cow")).isEqualTo(AnimalType.COW);
            assertThat(AnimalType.fromEntityTypeId("COW")).isEqualTo(AnimalType.COW);
        }

        @Test
        @DisplayName("should return null for unknown entity")
        void shouldReturnNullForUnknown() {
            assertThat(AnimalType.fromEntityTypeId("Unicorn")).isNull();
            assertThat(AnimalType.fromEntityTypeId("")).isNull();
            assertThat(AnimalType.fromEntityTypeId(null)).isNull();
        }
    }

    @Nested
    @DisplayName("fromModelAssetId()")
    class FromModelAssetId {

        @Test
        @DisplayName("should match adult model asset ID")
        void shouldMatchAdultModel() {
            assertThat(AnimalType.fromModelAssetId("Cow")).isEqualTo(AnimalType.COW);
            assertThat(AnimalType.fromModelAssetId("Wolf_Black")).isEqualTo(AnimalType.WOLF);
        }

        @Test
        @DisplayName("should match baby model asset ID")
        void shouldMatchBabyModel() {
            assertThat(AnimalType.fromModelAssetId("Calf")).isEqualTo(AnimalType.COW);
            assertThat(AnimalType.fromModelAssetId("Piglet")).isEqualTo(AnimalType.PIG);
            assertThat(AnimalType.fromModelAssetId("Chick")).isEqualTo(AnimalType.CHICKEN);
        }
    }

    @Nested
    @DisplayName("isBreedingFood()")
    class IsBreedingFood {

        @Test
        @DisplayName("should accept exact food match")
        void shouldAcceptExactMatch() {
            assertThat(AnimalType.COW.isBreedingFood("Plant_Crop_Cauliflower_Item")).isTrue();
            assertThat(AnimalType.PIG.isBreedingFood("Plant_Crop_Mushroom_Cap_Brown")).isTrue();
        }

        @Test
        @DisplayName("should accept case-insensitive match")
        void shouldAcceptCaseInsensitive() {
            assertThat(AnimalType.COW.isBreedingFood("plant_crop_cauliflower_item")).isTrue();
            assertThat(AnimalType.COW.isBreedingFood("PLANT_CROP_CAULIFLOWER_ITEM")).isTrue();
        }

        @Test
        @DisplayName("should reject wrong food")
        void shouldRejectWrongFood() {
            assertThat(AnimalType.COW.isBreedingFood("Carrot")).isFalse();
            assertThat(AnimalType.PIG.isBreedingFood("Wheat")).isFalse();
        }

        @Test
        @DisplayName("should handle null safely")
        void shouldHandleNull() {
            assertThat(AnimalType.COW.isBreedingFood(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isBabyVariant()")
    class IsBabyVariant {

        @Test
        @DisplayName("should identify baby model IDs")
        void shouldIdentifyBabyModels() {
            assertThat(AnimalType.isBabyVariant("Calf")).isTrue();
            assertThat(AnimalType.isBabyVariant("Piglet")).isTrue();
            assertThat(AnimalType.isBabyVariant("Chick")).isTrue();
            assertThat(AnimalType.isBabyVariant("Lamb")).isTrue();
        }

        @Test
        @DisplayName("should return false for adult models")
        void shouldReturnFalseForAdults() {
            assertThat(AnimalType.isBabyVariant("Cow")).isFalse();
            assertThat(AnimalType.isBabyVariant("Pig")).isFalse();
        }

        @Test
        @DisplayName("should handle null")
        void shouldHandleNull() {
            assertThat(AnimalType.isBabyVariant(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("hasBabyVariant()")
    class HasBabyVariant {

        @Test
        @DisplayName("livestock should have baby variants")
        void livestockShouldHaveBabyVariants() {
            assertThat(AnimalType.COW.hasBabyVariant()).isTrue();
            assertThat(AnimalType.PIG.hasBabyVariant()).isTrue();
            assertThat(AnimalType.CHICKEN.hasBabyVariant()).isTrue();
            assertThat(AnimalType.SHEEP.hasBabyVariant()).isTrue();
        }

        @Test
        @DisplayName("wild animals should not have baby variants")
        void wildAnimalsShouldNotHaveBabyVariants() {
            assertThat(AnimalType.WOLF.hasBabyVariant()).isFalse();
            assertThat(AnimalType.FOX.hasBabyVariant()).isFalse();
            assertThat(AnimalType.BEAR_GRIZZLY.hasBabyVariant()).isFalse();
        }
    }

    @Nested
    @DisplayName("getScaleForStage()")
    class GetScaleForStage {

        @Test
        @DisplayName("should return correct scales for growth stages")
        void shouldReturnCorrectScales() {
            assertThat(AnimalType.WOLF.getScaleForStage(GrowthStage.BABY)).isEqualTo(0.4f);
            assertThat(AnimalType.WOLF.getScaleForStage(GrowthStage.JUVENILE)).isEqualTo(0.7f);
            assertThat(AnimalType.WOLF.getScaleForStage(GrowthStage.ADULT)).isEqualTo(1.0f);
        }
    }

    @Nested
    @DisplayName("isMountable()")
    class IsMountable {

        @Test
        @DisplayName("should identify mountable animals")
        void shouldIdentifyMountable() {
            assertThat(AnimalType.HORSE.isMountable()).isTrue();
            assertThat(AnimalType.CAMEL.isMountable()).isTrue();
            assertThat(AnimalType.RAM.isMountable()).isTrue();
        }

        @Test
        @DisplayName("should identify non-mountable animals")
        void shouldIdentifyNonMountable() {
            assertThat(AnimalType.COW.isMountable()).isFalse();
            assertThat(AnimalType.PIG.isMountable()).isFalse();
            assertThat(AnimalType.WOLF.isMountable()).isFalse();
        }
    }

    @Nested
    @DisplayName("All animal types")
    class AllAnimalTypes {

        @Test
        @DisplayName("should all have required properties")
        void shouldAllHaveRequiredProperties() {
            for (AnimalType type : AnimalType.values()) {
                assertThat(type.getId()).as("id for %s", type).isNotNull().isNotEmpty();
                assertThat(type.getModelAssetId()).as("modelAssetId for %s", type).isNotNull().isNotEmpty();
                assertThat(type.getBreedingFood()).as("breedingFood for %s", type).isNotNull().isNotEmpty();
                assertThat(type.getCategory()).as("category for %s", type).isNotNull();
            }
        }

        @Test
        @DisplayName("livestock should all have baby variants")
        void livestockShouldHaveBabyVariants() {
            for (AnimalType type : AnimalType.values()) {
                if (type.isLivestock()) {
                    assertThat(type.hasBabyVariant())
                        .as("livestock %s should have baby variant", type)
                        .isTrue();
                    assertThat(type.getBabyNpcRoleId())
                        .as("livestock %s should have baby NPC role ID", type)
                        .isNotNull()
                        .isNotEmpty();
                }
            }
        }
    }
}
