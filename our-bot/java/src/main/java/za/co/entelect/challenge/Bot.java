package za.co.entelect.challenge;

import za.co.entelect.challenge.entities.Building;
import za.co.entelect.challenge.entities.CellStateContainer;
import za.co.entelect.challenge.entities.GameState;
import za.co.entelect.challenge.enums.BuildingType;
import za.co.entelect.challenge.enums.PlayerType;

import java.util.Collections;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.*;

public class Bot {

    private GameState gameState;

    public Bot(GameState gameState) {
        this.gameState = gameState;
        gameState.getGameMap();
    }

    public String run() {
        String command = "";

        //? Enemy buildings
        List<Integer> enemyEnergyEachRows   = getBuildingTypeEachRow(PlayerType.B, BuildingType.ENERGY);
        List<Integer> enemyAttackerEachRows = getBuildingTypeEachRow(PlayerType.B, BuildingType.ATTACK);
        List<Integer> enemyDefenseEachRows  = getBuildingTypeEachRow(PlayerType.B, BuildingType.DEFENSE);

        //? My buildings
        List<Integer> myEnergyEachRows      = getBuildingTypeEachRow(PlayerType.A, BuildingType.ENERGY);
        List<Integer> myAttackerEachRows    = getBuildingTypeEachRow(PlayerType.A, BuildingType.ATTACK);
        List<Integer> myDefenseEachRows     = getBuildingTypeEachRow(PlayerType.A, BuildingType.DEFENSE);


        //$ Greedy - aktifkan iron curtain(bangunan termahal bagi bot kami) bila bisa diaktifkan
        if (getEnergy(PlayerType.A) >= 100 && gameState.gameDetails.round >= 30 && gameState.getPlayers().get(0).ironCurtainAvailable) {
            boolean found = false;
            for(int i = 0 ; i <= 7 && !found ; i++) {
                for(int j = 0 ; j <= 7 && !found ; j++) {
                    if(isCellEmpty(i, j)) {
                        command = placeBuildingIn(BuildingType.IRON_CURTAIN, i, j);
                        found   = true;
                    }
                }
            }
        }

        //$ Greedy - Misalnya energy buildingnya kurang dari 9, bangun energy building dibagian belakang map
        if (command.equals("")) {
            int myEnergyBuildings   = myEnergyEachRows.stream().reduce(0, Integer::sum);
            if (myEnergyBuildings < 9) {
                for (int i = 0; i < gameState.gameDetails.mapHeight; i++) {
                    int enemyAttackOnRow = enemyAttackerEachRows.get(i);
                    int myEnergyOnRow    = myEnergyEachRows.get(i);
                    int myDefenseOnRow   = myDefenseEachRows.get(i);

                    if ((enemyAttackOnRow == 0 || myDefenseOnRow >= 1) && myEnergyOnRow == 0) {
                        if (canAffordBuilding(BuildingType.ENERGY))
                            command = placeBuildingInRowFromBack(BuildingType.ENERGY, i);
                        break;
                    }
                    //kalo lane nya aman , bangun disana
                    else if (enemyAttackOnRow-myDefenseOnRow <= 0) {
                        if (canAffordBuilding(BuildingType.ENERGY)) {
                            command = placeBuildingInRowFromBack(BuildingType.ENERGY, i);
                        }
                    }
                }
            }
        }

        //$ Greedy - dapatkan row dimana musuh attack buildingnya terbanyak, dan bangun defense building misalnya belum ada
        if (command.equals("")) {
            int maxNumberOfEnemyAttacker = Collections.max(enemyAttackerEachRows);
            if (maxNumberOfEnemyAttacker > 0) {
                int mostAttackedRow = enemyAttackerEachRows.indexOf(maxNumberOfEnemyAttacker);
                int myDefenseOnRow  = myDefenseEachRows.get(mostAttackedRow);
                if (myDefenseOnRow == 0 && canAffordBuilding(BuildingType.DEFENSE)) {
                    command = placeBuildingInRowFromFront(BuildingType.DEFENSE, mostAttackedRow);
                }
            }
        }

        //$ Greedy - bangun attack building di baris dimana baris musuh paling lemah
        if(command.equals("")){
            int minRow = -99;
            int minValue = 9999;
            for(int i = 0; i < gameState.gameDetails.mapHeight; i++) {
                int enemyAttackOnRow  = enemyAttackerEachRows.get(i);
                int enemyDefenseOnRow = enemyDefenseEachRows.get(i);
                int enemyEnergyOnRow  = enemyEnergyEachRows.get(i);
                int myAttackerOnRow   = myAttackerEachRows.get(i);
                int enemyRowStrength = (10 * enemyAttackOnRow) + (20 * enemyDefenseOnRow) + (5 * enemyEnergyOnRow) - (10 * myAttackerOnRow);
                
                if(minValue > enemyRowStrength) {
                    minValue = enemyRowStrength;
                    minRow = i;
                    break;
                }
            }
            if (minRow != -99 && canAffordBuilding(BuildingType.ATTACK)){
                command = placeBuildingInRowFromBack(BuildingType.ATTACK, minRow);
            }
        }

        return command;
    }

    /**
     * Place building in row y nearest to the front
     *
     * @param buildingType the building type
     * @param y            the y
     * @return the result
     **/
    private String placeBuildingInRowFromFront(BuildingType buildingType, int y) {
        for (int i = (gameState.gameDetails.mapWidth / 2) - 1; i >= 0; i--) {
            if (isCellEmpty(i, y)) {
                return buildCommand(i, y, buildingType);
            }
        }
        return "";
    }

    /**
     * Place building in row y nearest to the back
     *
     * @param buildingType the building type
     * @param y            the y
     * @return the result
     **/
    private String placeBuildingInRowFromBack(BuildingType buildingType, int y) {
        for (int i = 0; i < gameState.gameDetails.mapWidth / 2; i++) {
            if (isCellEmpty(i, y)) {
                return buildCommand(i, y, buildingType);
            }
        }
        return "";
    }

    /**
     * Place building in row y nearest to the back
     *
     * @param buildingType the building type
     * @param y            the y
     * @return the result
     **/
    private String placeBuildingIn(BuildingType buildingType, int x, int y) {
            if (isCellEmpty(x, y)) {
                return buildCommand(x, y, buildingType);
            }
        return "";
    }

    /**
     * Construct build command
     *
     * @param x            the x
     * @param y            the y
     * @param buildingType the building type
     * @return the result
     **/
    private String buildCommand(int x, int y, BuildingType buildingType) {
        return String.format("%s,%d,%s", String.valueOf(x), y, buildingType.getCommandCode());
    }

    //? GETTER
    // Dapatkan semua bangunan dengan tipe bangunan buildingType tiap row
    private List<Integer> getBuildingTypeEachRow(PlayerType playerType, BuildingType buildingType) {
        List<Integer> res = new ArrayList<>();
        for (int i = 0; i < gameState.gameDetails.mapHeight ; i++) {
            res.add(getAllBuildingsForPlayer(playerType, b -> b.buildingType == buildingType, i).size());
        }
        return res;
    }

    // Dapatkan semua bangunan untuk seorang player di barisan y
    private List<Building> getAllBuildingsForPlayer(PlayerType playerType, Predicate<Building> filter, int y) {
        return gameState.getGameMap().stream()
                .filter(c -> c.cellOwner == playerType && c.y == y)
                .flatMap(c -> c.getBuildings().stream())
                .filter(filter)
                .collect(Collectors.toList());
    }

    /**
     * Checks if cell at x,y is empty
     *
     * @param x the x
     * @param y the y
     * @return the result
     **/
    private boolean isCellEmpty(int x, int y) {
        Optional<CellStateContainer> cellOptional = gameState.getGameMap().stream()
                .filter(c -> c.x == x && c.y == y)
                .findFirst();

        if (cellOptional.isPresent()) {
            CellStateContainer cell = cellOptional.get();
            return cell.getBuildings().size() <= 0;
        } else {
            System.out.println("Invalid cell selected");
        }
        return true;
    }

    /**
     * Checks if building can be afforded
     *
     * @param buildingType the building type
     * @return the result
     **/
    private boolean canAffordBuilding(BuildingType buildingType) {
        return getEnergy(PlayerType.A) >= getPriceForBuilding(buildingType);
    }

    /**
     * Gets energy for player type
     *
     * @param playerType the player type
     * @return the result
     **/
    private int getEnergy(PlayerType playerType) {
        return gameState.getPlayers().stream()
                .filter(p -> p.playerType == playerType)
                .mapToInt(p -> p.energy)
                .sum();
    }

    /**
     * Gets price for building type
     *
     * @param buildingType the player type
     * @return the result
     **/
    private int getPriceForBuilding(BuildingType buildingType) {
        return gameState.gameDetails.buildingsStats.get(buildingType).price;
    }
}
