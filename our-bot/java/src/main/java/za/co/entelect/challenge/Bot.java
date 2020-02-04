package za.co.entelect.challenge;

import za.co.entelect.challenge.entities.Building;
import za.co.entelect.challenge.entities.Cell;
import za.co.entelect.challenge.entities.CellStateContainer;
import za.co.entelect.challenge.entities.GameState;
import za.co.entelect.challenge.enums.BuildingType;
import za.co.entelect.challenge.enums.PlayerType;

import java.util.Collections;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Bot {

    private GameState gameState;

    /**
     * Constructor
     *
     * @param gameState the game state
     **/
    public Bot(GameState gameState) {
        this.gameState = gameState;
        gameState.getGameMap();
    }

    /**
     * Run
     *
     * @return the result
     **/
    public String run() {
        String command = "";

        //Greedy - aktifkan iron curtain bila bisa diaktifkan
        if (command.equals("")) {
            if (canAffordBuilding(BuildingType.IRON_CURTAIN) && gameState.gameDetails.round >= 30) {
                command = placeBuildingIn(BuildingType.IRON_CURTAIN, 1, 1);
            }
        }

        //Greedy - dapatkan row dimana musuh attack buildingnya terbanyak, dan bangun defense building misalnya belum ada
        List<Integer> enemyAttackerByRows = getEnemyAttackersByRows();
        
        int myEnergyBuildings   = getMyBuildingsByType(BuildingType.ENERGY).size();
        int myAttackBuildings   = getMyBuildingsByType(BuildingType.ATTACK).size();
        //Misalnya energy buildingnya kurang dari 9, bangun energy building dibagian belakang map
        if (myEnergyBuildings < 9) {
            for (int i = 0; i < gameState.gameDetails.mapHeight; i++) {
                int enemyAttackOnRow = getAllBuildingsForPlayer(PlayerType.B, b -> b.buildingType == BuildingType.ATTACK, i).size();
                int myEnergyOnRow = getAllBuildingsForPlayer(PlayerType.A, b -> b.buildingType == BuildingType.ENERGY, i).size();

                if (enemyAttackOnRow == 0 && myEnergyOnRow <= 1) {
                    if (canAffordBuilding(BuildingType.ENERGY))
                        command = placeBuildingInRowFromBack(BuildingType.ENERGY, i);
                    break;
                }
                //Misalnya row terkiri udah diisi, bangun energy building di row paling kiri yang bisa dibangun
                // if (enemyAttackOnRow == 0 && myEnergyOnRow == 1) {
                //     if (canAffordBuilding(BuildingType.ENERGY))
                //         command = placeBuildingInRowFromBack(BuildingType.ENERGY, i);
                //     break;
                // }
            }
        }

        //Greedy - cari attack building musuh terbanyak dimana aku belom punya defense, lalu buat tembok dari depan
        if (command.equals("")) {
            List<Integer> enemyAttacks = new ArrayList<>();

            for (int i = 0; i< gameState.gameDetails.mapHeight ; i++) {
                int enemyAttackOnRow = getAllBuildingsForPlayer(PlayerType.B, b -> b.buildingType == BuildingType.ATTACK, i).size();
                int myDefenseOnRow   = getAllBuildingsForPlayer(PlayerType.A, b -> b.buildingType == BuildingType.DEFENSE, i).size();
            
                if (enemyAttackOnRow > 0 && myDefenseOnRow == 0) {
                    enemyAttacks.add(enemyAttackOnRow);
                }
                else {
                    enemyAttacks.add(0);
                }
            }
            int maxAttacker = Collections.max(enemyAttacks);
            if (maxAttacker > 0) {
                command = placeBuildingInRowFromFront(BuildingType.DEFENSE, enemyAttacks.indexOf(maxAttacker));
            }
        }
        //If the enemy has an attack building and I don't have a blocking wall, then block from the front.
        // for (int i = 0; i < gameState.gameDetails.mapHeight; i++) {
        //     int enemyAttackOnRow = getAllBuildingsForPlayer(PlayerType.B, b -> b.buildingType == BuildingType.ATTACK, i).size();
        //     int myDefenseOnRow = getAllBuildingsForPlayer(PlayerType.A, b -> b.buildingType == BuildingType.DEFENSE, i).size();
            
        //     if (enemyAttackOnRow > 0 && myDefenseOnRow == 0) {
        //         if (canAffordBuilding(BuildingType.DEFENSE))
        //             command = placeBuildingInRowFromFront(BuildingType.DEFENSE, i);
        //         else
        //             command = "";
        //         break;
        //     }
        // }

        //If there is a row where I don't have energy and there is no enemy attack building, then build energy in the back row.
        if (command.equals("")) {
            for (int i = 0; i < gameState.gameDetails.mapHeight; i++) {
                int enemyAttackOnRow = getAllBuildingsForPlayer(PlayerType.B, b -> b.buildingType == BuildingType.ATTACK, i).size();
                int myEnergyOnRow = getAllBuildingsForPlayer(PlayerType.A, b -> b.buildingType == BuildingType.ENERGY, i).size();

                if (enemyAttackOnRow == 0 && myEnergyOnRow == 0) {
                    if (canAffordBuilding(BuildingType.ENERGY))
                        command = placeBuildingInRowFromBack(BuildingType.ENERGY, i);
                    break;
                }
            }
        }

        //If I have a defense building on a row, then build an attack building behind it.
        if (command.equals("")) {
            for (int i = 0; i < gameState.gameDetails.mapHeight; i++) {
                if (getAllBuildingsForPlayer(PlayerType.A, b -> b.buildingType == BuildingType.DEFENSE, i).size() > 0
                        && canAffordBuilding(BuildingType.ATTACK)) {
                    command = placeBuildingInRowFromFront(BuildingType.ATTACK, i);

                }
            }
        }

        //If I don't need to do anything then either attack or defend randomly based on chance (65% attack, 35% defense).
        if (command.equals("")) {
            if (getEnergy(PlayerType.A) >= getMostExpensiveBuildingPrice()) {
                if ((new Random()).nextInt(100) <= 35) {
                    return placeBuildingRandomlyFromFront(BuildingType.DEFENSE);
                } else {
                    return placeBuildingRandomlyFromBack(BuildingType.ATTACK);
                }
            }
        }

        return command;
    }

    /**
     * Place building in a random row nearest to the back
     *
     * @param buildingType the building type
     * @return the result
     **/
    private String placeBuildingRandomlyFromBack(BuildingType buildingType) {
        for (int i = 0; i < gameState.gameDetails.mapWidth / 2; i++) {
            List<CellStateContainer> listOfFreeCells = getListOfEmptyCellsForColumn(i);
            if (!listOfFreeCells.isEmpty()) {
                CellStateContainer pickedCell = listOfFreeCells.get((new Random()).nextInt(listOfFreeCells.size()));
                return buildCommand(pickedCell.x, pickedCell.y, buildingType);
            }
        }
        return "";
    }

    /**
     * Place building in a random row nearest to the front
     *
     * @param buildingType the building type
     * @return the result
     **/
    private String placeBuildingRandomlyFromFront(BuildingType buildingType) {
        for (int i = (gameState.gameDetails.mapWidth / 2) - 1; i >= 0; i--) {
            List<CellStateContainer> listOfFreeCells = getListOfEmptyCellsForColumn(i);
            if (!listOfFreeCells.isEmpty()) {
                CellStateContainer pickedCell = listOfFreeCells.get((new Random()).nextInt(listOfFreeCells.size()));
                return buildCommand(pickedCell.x, pickedCell.y, buildingType);
            }
        }
        return "";
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

    /**
     * Get all buildings for player in row y
     *
     * @param playerType the player type
     * @param filter     the filter
     * @param y          the y
     * @return the result
     **/

    // ! GETTER
    private List<Building> getAllBuildingsForPlayer(PlayerType playerType, Predicate<Building> filter, int y) {
        return gameState.getGameMap().stream()
                .filter(c -> c.cellOwner == playerType && c.y == y)
                .flatMap(c -> c.getBuildings().stream())
                .filter(filter)
                .collect(Collectors.toList());
    }

    /**
     * Gets energy buildings for player type
     *
     * @param playerType the player type
     * @return the result
     **/
    private List<Building> getMyBuildingsByType(BuildingType buildingType) {
        return gameState.getGameMap().stream()
            .filter(c -> c.cellOwner == PlayerType.A)
            .flatMap(c -> c.getBuildings().stream())
            .filter(b -> b.buildingType == buildingType)
            .collect(Collectors.toList());
    }
    /**
     * Get all empty cells for column x
     *
     * @param x the x
     * @return the result
     **/
    private List<CellStateContainer> getListOfEmptyCellsForColumn(int x) {
        return gameState.getGameMap().stream()
                .filter(c -> c.x == x && isCellEmpty(x, c.y))
                .collect(Collectors.toList());
    }

    private List<Integer> getEnemyAttackersByRows() {
        List<Integer> res;
        return res;
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

    /**
     * Gets price for most expensive building type
     *
     * @return the result
     **/
    private int getMostExpensiveBuildingPrice() {
        return gameState.gameDetails.buildingsStats
                .values().stream()
                .mapToInt(b -> b.price)
                .max()
                .orElse(0);
    }
}
