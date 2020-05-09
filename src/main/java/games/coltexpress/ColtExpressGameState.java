package games.coltexpress;

import core.AbstractGameState;
import core.ForwardModel;
import core.actions.IAction;
import core.observations.IObservation;
import core.observations.IPrintable;
import games.coltexpress.actions.*;
import games.coltexpress.cards.CharacterType;
import games.coltexpress.cards.ColtExpressCard;
import games.coltexpress.components.Compartment;
import games.coltexpress.components.Loot;
import games.coltexpress.components.Train;
import core.components.PartialObservableDeck;
import utilities.Utils;

import java.util.*;

public class ColtExpressGameState extends AbstractGameState implements IObservation, IPrintable {

    public enum GamePhase {
        PlanActions,
        ExecuteActions,
        DraftCharacter
    }

    private List<PartialObservableDeck<ColtExpressCard>> playerHandCards;
    private List<PartialObservableDeck<ColtExpressCard>> playerDecks;
    private List<PartialObservableDeck<Loot>> playerLoot;
    private int[] bulletsLeft;
    protected PartialObservableDeck<ColtExpressCard> plannedActions;
    private HashMap<Integer, CharacterType> playerCharacters;
    private int playerPlayingBelle = -1;

    private Train train;
    public Train getTrain(){return train;}

    private GamePhase gamePhase = GamePhase.PlanActions;

    public static boolean PARTIAL_OBSERVABLE = false;

    public GamePhase getGamePhase() {
        return gamePhase;
    }

    public void setGamePhase(GamePhase gamePhase) {
        this.gamePhase = gamePhase;
    }

    public ColtExpressGameState(ColtExpressParameters gameParameters, ForwardModel model, int nPlayers) {
        super(gameParameters, model, nPlayers, new ColtExpressTurnOrder(nPlayers));
        setComponents(gameParameters);
    }

    public void setComponents(ColtExpressParameters gameParameters) {
        train = new Train(getNPlayers());
        playerCharacters = new HashMap<>();

        // give each player a single card
        playerDecks = new ArrayList<>(getNPlayers());
        playerHandCards = new ArrayList<>(getNPlayers());
        playerLoot = new ArrayList<>(getNPlayers());
        bulletsLeft = new int[getNPlayers()];
        plannedActions = new PartialObservableDeck<>("plannedActions", getNPlayers());

        Arrays.fill(bulletsLeft, 6);
        for (int playerIndex = 0; playerIndex < getNPlayers(); playerIndex++) {
            CharacterType characterType = gameParameters.pickRandomCharacterType();
            playerCharacters.put(playerIndex, characterType);
            if (characterType == CharacterType.Belle)
                playerPlayingBelle = playerIndex;

            boolean[] visibility = new boolean[getNPlayers()];
            Arrays.fill(visibility, !PARTIAL_OBSERVABLE);
            visibility[playerIndex] = true;

            PartialObservableDeck<ColtExpressCard> playerDeck =
                    new PartialObservableDeck<>("playerDeck"+playerIndex, visibility);
            for (ColtExpressCard.CardType type : gameParameters.cardCounts.keySet()){
                for (int j = 0; j < gameParameters.cardCounts.get(type); j++)
                    playerDeck.add(new ColtExpressCard(playerIndex, type));
            }
            playerDecks.add(playerDeck);
            playerDeck.shuffle();

            PartialObservableDeck<ColtExpressCard> playerHand = new PartialObservableDeck<>(
                    "playerHand"+playerIndex, visibility);

            playerHandCards.add(playerHand);

            PartialObservableDeck<Loot> loot = new PartialObservableDeck<>("playerLoot"+playerIndex, visibility);
            loot.add(new Loot(Loot.LootType.Purse, 250));
            playerLoot.add(loot);

            if (playerIndex%2 == 0)
                train.getCompartment(0).addPlayerInside(playerIndex);
            else
                train.getCompartment(1).addPlayerInside(playerIndex);

        }
        distributeCards();
    }

    public void distributeCards(){
        for (int playerIndex = 0; playerIndex < getNPlayers(); playerIndex++) {
            PartialObservableDeck<ColtExpressCard> playerHand = playerHandCards.get(playerIndex);
            PartialObservableDeck<ColtExpressCard> playerDeck = playerDecks.get(playerIndex);

            playerDeck.add(playerHand);
            playerHand.clear();

            for (int i = 0; i < 6; i++)
                playerHand.add(playerDeck.draw());
            if (playerCharacters.get(playerIndex) == CharacterType.Doc)
                playerHand.add(playerDeck.draw());
        }
    }

    public void addNeutralBullet(Integer playerID) {
        addBullet(playerID, -1);
    }

    public void addBullet(Integer playerID, Integer shooterID) {
        this.playerDecks.get(playerID).add(new ColtExpressCard(shooterID, ColtExpressCard.CardType.Bullet));
        if (playerCharacters.containsKey(shooterID))
            bulletsLeft[shooterID]--;
    }

    @Override
    public IObservation getObservation(int player) {
        return this;
    }

    @Override
    public void endGame() {
        this.gameStatus = Utils.GameResult.GAME_END;
        Arrays.fill(playerResults, Utils.GameResult.GAME_LOSE);
    }

    public ArrayList<IAction> schemingActions(int player){
        ArrayList<IAction> actions = new ArrayList<>();
        for (ColtExpressCard card : playerHandCards.get(player).getCards()){
            if (card.cardType == ColtExpressCard.CardType.Bullet)
                continue;

            actions.add(new SchemeAction(card, playerHandCards.get(player),
                    plannedActions, ((ColtExpressTurnOrder) turnOrder).isHiddenTurn()));

            // ghost can play a card hidden during the first turn
            if (playerCharacters.get(player) == CharacterType.Ghost &&
                    !((ColtExpressTurnOrder) turnOrder).isHiddenTurn() &&
                    ((ColtExpressTurnOrder) turnOrder).getCurrentRoundCardIndex() == 0)
                actions.add(new SchemeAction(card, playerHandCards.get(player),
                        plannedActions, true));
        }
        actions.add(new DrawCardsAction(playerHandCards.get(player), playerDecks.get(player)));
        return actions;
    }

    public ArrayList<IAction> stealingActions(int player)
    {
        ArrayList<IAction> actions = new ArrayList<>();
        ColtExpressCard plannedActionCard = plannedActions.peek(0);
        if (player == plannedActionCard.playerID)
        {
            switch (plannedActionCard.cardType){
                case Punch:
                    actions.add(new PunchAction(plannedActionCard, plannedActions,
                            playerDecks.get(player)));
                    break;
                case Shoot:
                    if (bulletsLeft[player] <= 0)
                        break;
                    else
                        createShootingActions(plannedActionCard, actions, player);
                    break;
                case MoveUp:
                    for (int i = 0; i < train.getSize(); i++){
                        Compartment compartment = train.getCompartment(i);
                        if (compartment.playersOnTopOfCompartment.contains(player)){
                            actions.add(new MoveVerticalAction(plannedActionCard, plannedActions,
                                    playerDecks.get(player), compartment, false));
                            break;
                        }
                        else if (compartment.playersInsideCompartment.contains(player)){
                            actions.add(new MoveVerticalAction(plannedActionCard, plannedActions,
                                    playerDecks.get(player), compartment, true));
                            break;
                        }
                    }
                    break;
                case MoveMarshal:
                    for (int i = 0; i < train.getSize(); i++){
                        Compartment compartment = train.getCompartment(i);
                        if (compartment.containsMarshal){
                            if (i-1 > 0)
                                actions.add(new MoveMarshalAction(plannedActionCard, plannedActions,
                                        playerDecks.get(player), compartment, train.getCompartment(i-1)));
                            if (i+1 < train.getSize())
                                actions.add(new MoveMarshalAction(plannedActionCard, plannedActions,
                                        playerDecks.get(player), compartment, train.getCompartment(i+1)));

                            break;
                        }
                    }
                    break;
                case CollectMoney:
                    actions.add(new CollectMoneyAction(plannedActionCard, plannedActions,
                            playerDecks.get(player)));
                    break;
                case MoveSideways:
                    for (int i = 0; i < train.getSize(); i++){
                        Compartment compartment = train.getCompartment(i);
                        if (compartment.playersOnTopOfCompartment.contains(player)){
                            for (int offset = 1; offset < 4; offset++){
                                if ((i-offset) > 0) {
                                    actions.add(new MoveSidewaysAction(plannedActionCard, plannedActions,
                                            playerDecks.get(player), compartment,
                                            train.getCompartment(i-offset)));
                                }
                                if ((i+offset) < train.getSize()) {
                                    actions.add(new MoveSidewaysAction(plannedActionCard, plannedActions,
                                            playerDecks.get(player), compartment,
                                            train.getCompartment(i+offset)));
                                }
                            }
                            break;
                        }
                        else if (compartment.playersInsideCompartment.contains(player)){
                            if ((i-1) > 0) {
                                actions.add(new MoveSidewaysAction(plannedActionCard, plannedActions,
                                        playerDecks.get(player), compartment,
                                        train.getCompartment(i-1)));
                            }
                            if ((i+1) < train.getSize()) {
                                actions.add(new MoveSidewaysAction(plannedActionCard, plannedActions,
                                        playerDecks.get(player), compartment,
                                        train.getCompartment(i+1)));
                            }
                            break;
                        }
                    }
                    break;
                case Bullet:
                    throw new IllegalArgumentException("Bullets cannot be played!");
                default:
                    throw new IllegalArgumentException("cardType " + plannedActionCard.cardType + "" +
                            " unknown to ColtExpressGameState");
            }

        }
        return actions;
    }

    private void createShootingActions(ColtExpressCard card, ArrayList<IAction> actions, int player) {
        int playerCompartmentIndex = 0;
        Compartment playerCompartment = null;
        boolean playerOnTop = false;
        for (int i = 0; i < train.getSize(); i++)
        {
            Compartment compartment = train.getCompartment(i);
            if (compartment.playersOnTopOfCompartment.contains(player)) {
                playerCompartmentIndex = i;
                playerCompartment = compartment;
                playerOnTop = true;
                break;
            } else if (compartment.playersInsideCompartment.contains(player)){
                playerCompartmentIndex = i;
                playerCompartment = compartment; //todo add break;
            }
        }

        HashMap<Integer, Compartment> targets = new HashMap<>();

        if (playerOnTop){
            //shots in rear direction
            for (int offset = 0; playerCompartmentIndex-offset >=0; offset++){
                Compartment targetCompartment = train.getCompartment(playerCompartmentIndex-offset);
                if (targetCompartment.playersOnTopOfCompartment.size() > 0){
                    for (Integer target : targetCompartment.playersOnTopOfCompartment)
                        targets.put(target, targetCompartment);
                    break;
                }
            }

            //shots to the front of the train
            for (int offset = 0; playerCompartmentIndex+offset < train.getSize(); offset++){
                Compartment targetCompartment = train.getCompartment(playerCompartmentIndex+offset);
                if (targetCompartment.playersOnTopOfCompartment.size() > 0){
                    for (Integer target : targetCompartment.playersOnTopOfCompartment)
                        targets.put(target, targetCompartment);
                    break;
                }
            }

            //add player below if your are tuco
            if (playerCharacters.get(player) == CharacterType.Tuco){
                for (Integer target : train.getCompartment(playerCompartmentIndex).playersInsideCompartment)
                    targets.put(target, playerCompartment);
            }
        } else {
            if (playerCompartmentIndex - 1 >= 0){
                Compartment targetCompartment = train.getCompartment(playerCompartmentIndex-1);
                if (targetCompartment.playersInsideCompartment.size() > 0){
                    for (Integer target : targetCompartment.playersInsideCompartment)
                        targets.put(target, targetCompartment);
                }
            }

            if (playerCompartmentIndex + 1 < train.getSize()){
                Compartment targetCompartment = train.getCompartment(playerCompartmentIndex+1);
                if (targetCompartment.playersInsideCompartment.size() > 0){
                    for (Integer target : targetCompartment.playersInsideCompartment)
                        targets.put(target, targetCompartment);
                }
            }

            //add player below if your are tuco
            if (playerCharacters.get(player) == CharacterType.Tuco){
                for (Integer target : train.getCompartment(playerCompartmentIndex).playersOnTopOfCompartment)
                    targets.put(target, playerCompartment);
            }
        }

        if (targets.size() > 1 && targets.containsKey(playerPlayingBelle))
            targets.remove(playerPlayingBelle);

        boolean playerIsDjango = playerCharacters.get(player) == CharacterType.Django;
        for (Map.Entry<Integer, Compartment> entry : targets.entrySet()){
            actions.add(new ShootPlayerAction(card, plannedActions, playerDecks.get(player), playerCompartment,
                    entry.getKey(), entry.getValue(), playerIsDjango));
        }
    }

    @Override
    public List<IAction> computeAvailableActions() {

        ArrayList<IAction> actions;
        int player = getTurnOrder().getCurrentPlayer(this);
        switch (gamePhase){
            case DraftCharacter:
                System.out.println("character drafting is not implemented yet");
            case PlanActions:
                actions = schemingActions(player);
                break;
            case ExecuteActions:
                actions = stealingActions(player);
                break;
            default:
                actions = new ArrayList<>();
                break;
        }

        return actions;
    }

    @Override
    public void setComponents() {

    }

    @Override
    public void printToConsole() {
        System.out.println("Colt Express Game-State");
        System.out.println("=======================");

        int currentPlayer = turnOrder.getCurrentPlayer(this);

        for (int i = 0; i < getNPlayers(); i++){
            if (currentPlayer == i)
                System.out.print(">>> ");
            System.out.print("Player " + i + " = "+ playerCharacters.get(i).name() + ":  ");
            System.out.print("Hand=");
            System.out.print(playerHandCards.get(i).toString(i));
            System.out.print("; Deck=");
            System.out.print(playerDecks.get(i).toString(i));
            System.out.print("; Loot=");
            System.out.print(playerLoot.get(i).toString(i));
            System.out.println();
        }
        System.out.println();
        System.out.println(train.toString());

        System.out.println();
        System.out.print("Planned Actions: ");
        System.out.println(plannedActions.toString());

        System.out.println();
        System.out.println(turnOrder.toString());

        System.out.println();
        System.out.println("Current GamePhase: " + gamePhase);
    }


}
