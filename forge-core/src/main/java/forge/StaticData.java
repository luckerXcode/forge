package forge;

import com.google.common.base.Predicate;
import forge.card.CardDb;
import forge.card.CardEdition;
import forge.card.CardRules;
import forge.card.PrintSheet;
import forge.item.BoosterBox;
import forge.item.FatPack;
import forge.item.PaperCard;
import forge.item.SealedProduct;
import forge.token.TokenDb;
import forge.util.TextUtil;
import forge.util.storage.IStorage;
import forge.util.storage.StorageBase;

import java.io.File;
import java.util.*;


/**
 * The class holding game invariants, such as cards, editions, game formats. All that data, which is not supposed to be changed by player
 *
 * @author Max
 */
public class StaticData {
    private final CardStorageReader cardReader;
    private final CardStorageReader tokenReader;
    private final CardStorageReader customCardReader;

    private final String blockDataFolder;
    private final CardDb commonCards;
    private final CardDb variantCards;
    private final CardDb customCards;
    private final TokenDb allTokens;
    private final CardEdition.Collection editions;
    private final CardEdition.Collection customEditions;

    private Predicate<PaperCard> standardPredicate;
    private Predicate<PaperCard> brawlPredicate;
    private Predicate<PaperCard> pioneerPredicate;
    private Predicate<PaperCard> modernPredicate;
    private Predicate<PaperCard> commanderPredicate;
    private Predicate<PaperCard> oathbreakerPredicate;

    private boolean filteredHandsEnabled = false;

    private MulliganDefs.MulliganRule mulliganRule = MulliganDefs.getDefaultRule();

    private boolean allowCustomCardsInDecksConformance;
    private boolean enableSmartCardArtSelection;

    // Loaded lazily:
    private IStorage<SealedProduct.Template> boosters;
    private IStorage<SealedProduct.Template> specialBoosters;
    private IStorage<SealedProduct.Template> tournaments;
    private IStorage<FatPack.Template> fatPacks;
    private IStorage<BoosterBox.Template> boosterBoxes;
    private IStorage<PrintSheet> printSheets;

    private static StaticData lastInstance = null;

    public StaticData(CardStorageReader cardReader, CardStorageReader customCardReader, String editionFolder, String customEditionsFolder, String blockDataFolder, String cardArtPreference, boolean enableUnknownCards, boolean loadNonLegalCards) {
        this(cardReader, null, customCardReader, editionFolder, customEditionsFolder, blockDataFolder, cardArtPreference, enableUnknownCards, loadNonLegalCards, false, false);
    }

    public StaticData(CardStorageReader cardReader, CardStorageReader tokenReader, CardStorageReader customCardReader, String editionFolder, String customEditionsFolder, String blockDataFolder, String cardArtPreference, boolean enableUnknownCards, boolean loadNonLegalCards, boolean allowCustomCardsInDecksConformance){
        this(cardReader, tokenReader, customCardReader, editionFolder, customEditionsFolder, blockDataFolder, cardArtPreference, enableUnknownCards, loadNonLegalCards, allowCustomCardsInDecksConformance, false);
    }

    public StaticData(CardStorageReader cardReader, CardStorageReader tokenReader, CardStorageReader customCardReader, String editionFolder, String customEditionsFolder, String blockDataFolder, String cardArtPreference, boolean enableUnknownCards, boolean loadNonLegalCards, boolean allowCustomCardsInDecksConformance, boolean enableSmartCardArtSelection) {
        this.cardReader = cardReader;
        this.tokenReader = tokenReader;
        this.editions = new CardEdition.Collection(new CardEdition.Reader(new File(editionFolder)));
        this.blockDataFolder = blockDataFolder;
        this.customCardReader = customCardReader;
        this.customEditions = new CardEdition.Collection(new CardEdition.Reader(new File(customEditionsFolder), true));
        this.allowCustomCardsInDecksConformance = allowCustomCardsInDecksConformance;
        this.enableSmartCardArtSelection = enableSmartCardArtSelection;
        lastInstance = this;
        List<String> funnyCards = new ArrayList<>();
        List<String> filtered = new ArrayList<>();

        {
            final Map<String, CardRules> regularCards = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            final Map<String, CardRules> variantsCards = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            final Map<String, CardRules> customizedCards = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

            for (CardEdition e : editions) {
                if (e.getType() == CardEdition.Type.FUNNY || e.getBorderColor() == CardEdition.BorderColor.SILVER) {
                    for (CardEdition.CardInSet cis : e.getAllCardsInSet()) {
                        funnyCards.add(cis.name);
                    }
                }
            }

            for (CardRules card : cardReader.loadCards()) {
                if (null == card) continue;

                final String cardName = card.getName();

                if (!loadNonLegalCards && !card.getType().isBasicLand() && funnyCards.contains(cardName))
                    filtered.add(cardName);

                if (card.isVariant()) {
                    variantsCards.put(cardName, card);
                } else {
                    regularCards.put(cardName, card);
                }
            }
            if (customCardReader != null) {
                for (CardRules card : customCardReader.loadCards()) {
                    if (null == card) continue;

                    final String cardName = card.getName();
                    customizedCards.put(cardName, card);
                }
            }

            if (!filtered.isEmpty()) {
                Collections.sort(filtered);
            }

            commonCards = new CardDb(regularCards, editions, filtered, cardArtPreference);
            variantCards = new CardDb(variantsCards, editions, filtered, cardArtPreference);
            customCards = new CardDb(customizedCards, customEditions, filtered, cardArtPreference);

            //must initialize after establish field values for the sake of card image logic
            commonCards.initialize(false, false, enableUnknownCards);
            variantCards.initialize(false, false, enableUnknownCards);
            customCards.initialize(false, false, enableUnknownCards);
        }

        if (this.tokenReader != null){
            final Map<String, CardRules> tokens = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

            for (CardRules card : this.tokenReader.loadCards()) {
                if (null == card) continue;

                tokens.put(card.getNormalizedName(), card);
            }
            allTokens = new TokenDb(tokens, editions);
        } else {
            allTokens = null;
        }
    }

    public static StaticData instance() {
        return lastInstance;
    }

    public final CardEdition.Collection getEditions() {
        return this.editions;
    }

    public final CardEdition.Collection getCustomEditions(){
        return this.customEditions;
    }


    private List<CardEdition> sortedEditions;
    public final List<CardEdition> getSortedEditions() {
        if (sortedEditions == null) {
            sortedEditions = new ArrayList<>();
            for (CardEdition set : editions) {
                sortedEditions.add(set);
            }
            if (customEditions.size() > 0){
                for (CardEdition set : customEditions) {
                    sortedEditions.add(set);
                }
            }
            Collections.sort(sortedEditions);
            Collections.reverse(sortedEditions); //put newer sets at the top
        }
        return sortedEditions;
    }

    private TreeMap<CardEdition.Type, List<CardEdition>> editionsTypeMap;
    public final Map<CardEdition.Type, List<CardEdition>> getEditionsTypeMap(){
        if (editionsTypeMap == null){
            editionsTypeMap = new TreeMap<>();
            for (CardEdition.Type editionType : CardEdition.Type.values()){
                editionsTypeMap.put(editionType, new ArrayList<>());
            }
            for (CardEdition edition : this.getSortedEditions()){
                CardEdition.Type key = edition.getType();
                List<CardEdition> editionsOfType = editionsTypeMap.get(key);
                editionsOfType.add(edition);
            }
        }
        return editionsTypeMap;
    }

    public CardEdition getCardEdition(String setCode){
        CardEdition edition = this.editions.get(setCode);
        if (edition == null)  // try custom editions
            edition = this.customEditions.get(setCode);
        return edition;
    }

    public PaperCard getOrLoadCommonCard(String cardName, String setCode, int artIndex, boolean foil) {
        PaperCard card = commonCards.getCard(cardName, setCode, artIndex);
        if (card == null) {
            attemptToLoadCard(cardName, setCode);
            card = commonCards.getCard(cardName, setCode, artIndex);
        }
        if (card == null)
            card = commonCards.getCard(cardName, setCode);
        if (card == null)
            card = customCards.getCard(cardName, setCode, artIndex);
        if (card == null)
            card = customCards.getCard(cardName, setCode);
        if (card == null)
            return null;
        return foil ? card.getFoiled() : card;
    }

    public void attemptToLoadCard(String cardName){
        this.attemptToLoadCard(cardName, null);
    }

    public void attemptToLoadCard(String cardName, String setCode){
        CardRules rules = cardReader.attemptToLoadCard(cardName);
        CardRules customRules = null;
        if (customCardReader != null) {
            customRules = customCardReader.attemptToLoadCard(cardName);
        }
        if (rules != null) {
            if (rules.isVariant()) {
                variantCards.loadCard(cardName, setCode, rules);
            } else {
                commonCards.loadCard(cardName, setCode, rules);
            }
        }
        if (customRules != null) {
            customCards.loadCard(cardName, setCode, customRules);
        }
    }

    /** @return {@link forge.util.storage.IStorage}<{@link forge.item.SealedProduct.Template}> */
    public final IStorage<SealedProduct.Template> getTournamentPacks() {
        if (tournaments == null)
            tournaments = new StorageBase<>("Starter sets", new SealedProduct.Template.Reader(new File(blockDataFolder, "starters.txt")));
        return tournaments;
    }

    /** @return {@link forge.util.storage.IStorage}<{@link forge.item.SealedProduct.Template}> */
    public final IStorage<SealedProduct.Template> getBoosters() {
        if (boosters == null)
            boosters = new StorageBase<>("Boosters", editions.getBoosterGenerator());
        return boosters;
    }

    public final IStorage<SealedProduct.Template> getSpecialBoosters() {
        if (specialBoosters == null)
            specialBoosters = new StorageBase<>("Special boosters", new SealedProduct.Template.Reader(new File(blockDataFolder, "boosters-special.txt")));
        return specialBoosters;
    }

    public IStorage<PrintSheet> getPrintSheets() {
        if (printSheets == null)
            printSheets = PrintSheet.initializePrintSheets(new File(blockDataFolder, "printsheets.txt"), getEditions());
        return printSheets;
    }

    public CardDb getCommonCards() {
        return commonCards;
    }

    public CardDb getCustomCards() {
        return customCards;
    }

    public CardDb getVariantCards() {
        return variantCards;
    }

    public Map<String, CardDb> getAvailableDatabases(){
        Map<String, CardDb> databases = new HashMap<>();
        databases.put("Common", commonCards);
        databases.put("Custom", customCards);
        databases.put("Variant", variantCards);
        return databases;
    }


    public TokenDb getAllTokens() { return allTokens; }

    public boolean allowCustomCardsInDecksConformance() {
        return this.allowCustomCardsInDecksConformance;
    }


    public void setStandardPredicate(Predicate<PaperCard> standardPredicate) { this.standardPredicate = standardPredicate; }

    public void setPioneerPredicate(Predicate<PaperCard> pioneerPredicate) { this.pioneerPredicate = pioneerPredicate; }

    public void setModernPredicate(Predicate<PaperCard> modernPredicate) { this.modernPredicate = modernPredicate; }

    public void setCommanderPredicate(Predicate<PaperCard> commanderPredicate) { this.commanderPredicate = commanderPredicate; }

    public void setOathbreakerPredicate(Predicate<PaperCard> oathbreakerPredicate) { this.oathbreakerPredicate = oathbreakerPredicate; }

    public void setBrawlPredicate(Predicate<PaperCard> brawlPredicate) { this.brawlPredicate = brawlPredicate; }

    public Predicate<PaperCard> getStandardPredicate() { return standardPredicate; }
    
    public Predicate<PaperCard> getPioneerPredicate() { return pioneerPredicate; }

    public Predicate<PaperCard> getModernPredicate() { return modernPredicate; }

    public Predicate<PaperCard> getCommanderPredicate() { return commanderPredicate; }

    public Predicate<PaperCard> getOathbreakerPredicate() { return oathbreakerPredicate; }

    public Predicate<PaperCard> getBrawlPredicate() { return brawlPredicate; }

    public void setFilteredHandsEnabled(boolean filteredHandsEnabled){
        this.filteredHandsEnabled = filteredHandsEnabled;
    }

    /**
     * Get an alternative card print for the given card wrt. the input setReleaseDate.
     * The reference release date will be used to retrieve the alternative art, according
     * to the Card Art Preference settings.
     *
     * Note: if input card is Foil, and an alternative card art is found, it will be returned foil too!
     *
     * @see StaticData#getAlternativeCardPrint(forge.item.PaperCard, java.util.Date)
     * @param card Input Reference Card
     * @param setReleaseDate reference set release date
     * @return Alternative Card Art (from a different edition) of input card, or null if not found.
     */
    public PaperCard getAlternativeCardPrint(PaperCard card, final Date setReleaseDate) {
        boolean isCardArtPreferenceLatestArt = this.cardArtPreferenceIsLatest();
        boolean cardArtPreferenceHasFilter = this.isCoreExpansionOnlyFilterSet();
        return this.getAlternativeCardPrint(card, setReleaseDate, isCardArtPreferenceLatestArt,
                                            cardArtPreferenceHasFilter);
    }

    /**
     * Retrieve an alternative card print for a given card, and the input reference set release date.
     * The <code>setReleaseDate</code> will be used depending on the desired Card Art Preference policy to apply
     * when looking for alternative card, namely <code>Latest Art</code> and <i>with</i> or <i>without</i> filters
     * on editions.
     *
     * In more details:
     * - If card art preference is Latest Art first, the alternative card print will be chosen from
     * the first edition that has been released **after** the reference date.
     * - Conversely, if card art preference is Original Art first, the alternative card print will be
     * chosen from the first edition that has been released **before** the reference date.
     *
     * The rationale behind this strategy is to select an alternative card print from the lower-bound extreme
     * (upper-bound extreme) among the latest (original) editions where the card can be found.
     *
     * @param card  The instance of <code>PaperCard</code> to look for an alternative print
     * @param setReleaseDate  The reference release date used to control the search for alternative card print.
     *                        The chose candidate will be gathered from an edition printed before (upper bound) or
     *                        after (lower bound) the reference set release date.
     * @param isCardArtPreferenceLatestArt  Determines whether or not "Latest Art" Card Art preference should be used
     *                                      when looking for an alternative candidate print.
     * @param cardArtPreferenceHasFilter    Determines whether or not the search should only consider
     *                                      Core, Expansions, or Reprints sets when looking for alternative candidates.
     * @return  an instance of <code>PaperCard</code> that is the selected alternative candidate, or <code>null</code>
     * if None could be found.
     */
    public PaperCard getAlternativeCardPrint(PaperCard card, Date setReleaseDate,
                                             boolean isCardArtPreferenceLatestArt,
                                             boolean cardArtPreferenceHasFilter){
        Date searchReferenceDate = getReferenceDate(setReleaseDate, isCardArtPreferenceLatestArt);
        CardDb.CardArtPreference searchCardArtStrategy = getSearchStrategyForAlternativeCardArt(isCardArtPreferenceLatestArt,
                                                                          cardArtPreferenceHasFilter);
        return searchAlternativeCardCandidate(card, isCardArtPreferenceLatestArt, searchReferenceDate,
                                              searchCardArtStrategy);
    }

    /**
     * This method extends the defatult <code>getAlternativeCardPrint</code> with extra settings to be used for
     * alternative card print.
     *
     * <p>
     * These options for Alternative Card Print make sense as part of the harmonisation/theme-matching process for
     * cards in Deck Sections (i.e. CardPool). In fact, the values of the provided flags for alternative print
     * for a single card will be determined according to whole card pool (Deck section) the card appears in.
     *
     * @param card  The instance of <code>PaperCard</code> to look for an alternative print
     * @param setReleaseDate  The reference release date used to control the search for alternative card print.
     *                        The chose candidate will be gathered from an edition printed before (upper bound) or
     *                        after (lower bound) the reference set release date.
     * @param isCardArtPreferenceLatestArt  Determines whether or not "Latest Art" Card Art preference should be used
     *                                      when looking for an alternative candidate print.
     * @param cardArtPreferenceHasFilter    Determines whether or not the search should only consider
     *                                      Core, Expansions, or Reprints sets when looking for alternative candidates.
     * @param preferCandidatesFromExpansionSets Whenever the selected Card Art Preference has filter, try to get
     *                                          prefer candidates from Expansion Sets over those in Core or Reprint
     *                                          Editions (whenever possible)
     *                                          e.g. Necropotence from Ice Age rather than 5th Edition (w/ Latest=false)
     * @param preferModernFrame  If True, Modern Card Frame will be preferred over Old Frames.
     * @return an instance of <code>PaperCard</code> that is the selected alternative candidate, or <code>null</code>
     *          if None could be found.
     */
    public PaperCard getAlternativeCardPrint(PaperCard card, Date setReleaseDate, boolean isCardArtPreferenceLatestArt,
                                             boolean cardArtPreferenceHasFilter,
                                             boolean preferCandidatesFromExpansionSets, boolean preferModernFrame) {

        PaperCard altCard = this.getAlternativeCardPrint(card, setReleaseDate, isCardArtPreferenceLatestArt,
                                                         cardArtPreferenceHasFilter);
        if (altCard == null)
            return altCard;
        // from here on, we're sure we do have a candidate already!

        /* Try to refine selection by getting one candidate with frame matching current
           Card Art Preference (that is NOT the lookup strategy!)*/
        PaperCard refinedAltCandidate = this.tryToGetCardPrintWithMatchingFrame(altCard,
                isCardArtPreferenceLatestArt,
                cardArtPreferenceHasFilter,
                preferModernFrame);
        if (refinedAltCandidate != null)
            altCard = refinedAltCandidate;

        if (cardArtPreferenceHasFilter && preferCandidatesFromExpansionSets){
            /* Now try to refine selection by looking for an alternative choice extracted from an Expansion Set.
               NOTE: At this stage, any future selection should be already compliant with previous filter on
               Card Frame (if applied) given that we'll be moving either UP or DOWN the timeline of Card Edition */
            refinedAltCandidate = this.tryToGetCardPrintFromExpansionSet(altCard, isCardArtPreferenceLatestArt,
                                                                         preferModernFrame);
            if (refinedAltCandidate != null)
                altCard = refinedAltCandidate;
        }
        return altCard;
    }

    private PaperCard searchAlternativeCardCandidate(PaperCard card, boolean isCardArtPreferenceLatestArt,
                                                     Date searchReferenceDate,
                                                     CardDb.CardArtPreference searchCardArtStrategy) {
        // Note: this won't apply to Custom Nor Variant Cards, so won't bother including it!
        CardDb cardDb = this.commonCards;
        String cardName = card.getName();
        int artIndex = card.getArtIndex();
        PaperCard altCard = null;

        if (isCardArtPreferenceLatestArt) {  // RELEASED AFTER REFERENCE DATE
            altCard = cardDb.getCardFromEditionsReleasedAfter(cardName, searchCardArtStrategy, artIndex, searchReferenceDate);
            if (altCard == null)  // relax artIndex condition
                altCard = cardDb.getCardFromEditionsReleasedAfter(cardName, searchCardArtStrategy, searchReferenceDate);
        } else {  // RELEASED BEFORE REFERENCE DATE
            altCard = cardDb.getCardFromEditionsReleasedBefore(cardName, searchCardArtStrategy, artIndex, searchReferenceDate);
            if (altCard == null)  // relax artIndex constraint
                altCard = cardDb.getCardFromEditionsReleasedBefore(cardName, searchCardArtStrategy, searchReferenceDate);
        }
        if (altCard == null)
            return null;
        return card.isFoil() ? altCard.getFoiled() : altCard;
    }

    private Date getReferenceDate(Date setReleaseDate, boolean isCardArtPreferenceLatestArt) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(setReleaseDate);
        if (isCardArtPreferenceLatestArt)
            cal.add(Calendar.DATE, -2);  // go two days behind to also include the original reference set
        else
            cal.add(Calendar.DATE, 2);  // go two days ahead to also include the original reference set
        return cal.getTime();
    }

    private CardDb.CardArtPreference getSearchStrategyForAlternativeCardArt(boolean isCardArtPreferenceLatestArt, boolean cardArtPreferenceHasFilter) {
        CardDb.CardArtPreference lookupStrategy;
        if (isCardArtPreferenceLatestArt) {
            // Get Lower bound (w/ Original Art and Edition Released AFTER Pivot Date)
            if (cardArtPreferenceHasFilter)
                lookupStrategy = CardDb.CardArtPreference.ORIGINAL_ART_CORE_EXPANSIONS_REPRINT_ONLY;  // keep the filter
            else
                lookupStrategy = CardDb.CardArtPreference.ORIGINAL_ART_ALL_EDITIONS;
        } else {
            // Get Upper bound (w/ Latest Art and Edition released BEFORE Pivot Date)
            if (cardArtPreferenceHasFilter)
                lookupStrategy = CardDb.CardArtPreference.LATEST_ART_CORE_EXPANSIONS_REPRINT_ONLY;  // keep the filter
            else
                lookupStrategy = CardDb.CardArtPreference.LATEST_ART_ALL_EDITIONS;
        }
        return lookupStrategy;
    }

    private PaperCard tryToGetCardPrintFromExpansionSet(PaperCard altCard,
                                                        boolean isCardArtPreferenceLatestArt,
                                                        boolean preferModernFrame){
        CardEdition altCardEdition = editions.get(altCard.getEdition());
        if (altCardEdition.getType() == CardEdition.Type.EXPANSION)
            return null;  // Nothing to do here!
        boolean searchStrategyFlag = (isCardArtPreferenceLatestArt == preferModernFrame) == isCardArtPreferenceLatestArt;
        // We'll force the filter on to strictly reduce the alternative candidates retrieved to those
        // from Expansions, Core, and Reprint sets.
        CardDb.CardArtPreference searchStrategy = getSearchStrategyForAlternativeCardArt(searchStrategyFlag,
                                                                                         true);
        PaperCard altCandidate = altCard;
        while (altCandidate != null){
            Date referenceDate = editions.get(altCandidate.getEdition()).getDate();
            altCandidate = this.searchAlternativeCardCandidate(altCandidate, preferModernFrame,
                                                                referenceDate, searchStrategy);
            if (altCandidate != null) {
                CardEdition altCandidateEdition = editions.get(altCandidate.getEdition());
                if (altCandidateEdition.getType() == CardEdition.Type.EXPANSION)
                    break;
            }
        }
        // this will be either a true candidate or null if the cycle broke because of no other suitable candidates
        return altCandidate;
    }

    private PaperCard tryToGetCardPrintWithMatchingFrame(PaperCard altCard,
                                                         boolean isCardArtPreferenceLatestArt,
                                                         boolean cardArtHasFilter,
                                                         boolean preferModernFrame){
        CardEdition altCardEdition = editions.get(altCard.getEdition());
        boolean frameIsCompliantAlready = (altCardEdition.isModern() == preferModernFrame);
        if (frameIsCompliantAlready)
            return null;  // Nothing to do here!
        boolean searchStrategyFlag = (isCardArtPreferenceLatestArt == preferModernFrame) == isCardArtPreferenceLatestArt;
        CardDb.CardArtPreference searchStrategy = getSearchStrategyForAlternativeCardArt(searchStrategyFlag,
                                                                                         cardArtHasFilter);
        PaperCard altCandidate = altCard;
        while (altCandidate != null){
            Date referenceDate = editions.get(altCandidate.getEdition()).getDate();
            altCandidate = this.searchAlternativeCardCandidate(altCandidate, preferModernFrame,
                                                               referenceDate, searchStrategy);
            if (altCandidate != null) {
                CardEdition altCandidateEdition = editions.get(altCandidate.getEdition());
                if (altCandidateEdition.isModern() == preferModernFrame)
                    break;
            }
        }
        // this will be either a true candidate or null if the cycle broke because of no other suitable candidates
        return altCandidate;
    }



    /**
     * Get the Art Count for a given <code>PaperCard</code> looking for a candidate in all
     * available databases.
     *
     * @param card Instance of target <code>PaperCard</code>
     * @return The number of available arts for the given card in the corresponding set, or 0 if not found.
     */
    public int getCardArtCount(PaperCard card){
        Collection<CardDb> databases = this.getAvailableDatabases().values();
        for (CardDb db: databases){
            int artCount = db.getArtCount(card.getName(), card.getEdition());
            if (artCount > 0)
                return artCount;
        }
        return 0;
    }

    public boolean getFilteredHandsEnabled(){
        return filteredHandsEnabled;
    }

    public void setMulliganRule(MulliganDefs.MulliganRule rule) {
        mulliganRule = rule;
    }

    public MulliganDefs.MulliganRule getMulliganRule() {
        return mulliganRule;
    }

    public void setCardArtPreference(boolean latestArt, boolean coreExpansionOnly){
        this.commonCards.setCardArtPreference(latestArt, coreExpansionOnly);
        this.variantCards.setCardArtPreference(latestArt, coreExpansionOnly);
        this.customCards.setCardArtPreference(latestArt, coreExpansionOnly);
    }

    public String getCardArtPreferenceName(){
        return this.commonCards.getCardArtPreference().toString();
    }

    public CardDb.CardArtPreference getCardArtPreference(){
        return this.commonCards.getCardArtPreference();
    }


    public boolean isCoreExpansionOnlyFilterSet(){ return this.commonCards.getCardArtPreference().filterSets; }

    public boolean cardArtPreferenceIsLatest(){
        return this.commonCards.getCardArtPreference().latestFirst;
    }

    // === MOBILE APP Alternative Methods (using String Labels, not yet localised!!) ===
    // Note: only used in mobile
    public String[] getCardArtAvailablePreferences(){
        CardDb.CardArtPreference[] preferences = CardDb.CardArtPreference.values();
        String[] preferences_avails = new String[preferences.length];
        for (int i = 0; i < preferences.length; i++) {
            StringBuilder label = new StringBuilder();
            String[] fullNames = preferences[i].toString().split("_");
            for (String name : fullNames)
                label.append(TextUtil.capitalize(name.toLowerCase())).append(" ");
            preferences_avails[i] = label.toString().trim();
        }
        return preferences_avails;
    }
    public void setCardArtPreference(String artPreference){
        this.commonCards.setCardArtPreference(artPreference);
        this.variantCards.setCardArtPreference(artPreference);
        this.customCards.setCardArtPreference(artPreference);
    }

    //
    public boolean isEnabledCardArtSmartSelection(){
        return this.enableSmartCardArtSelection;
    }
    public void setEnableSmartCardArtSelection(boolean isEnabled){
        this.enableSmartCardArtSelection = isEnabled;
    }

}
