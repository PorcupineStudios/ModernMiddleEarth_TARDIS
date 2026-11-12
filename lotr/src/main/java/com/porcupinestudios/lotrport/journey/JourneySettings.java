package com.porcupinestudios.lotrport.journey;

/**
 * Journey numbers ({@code journey/settings.json}).
 *
 * @param walkSpeed blocks a second a party on foot makes on open grassland (speed 1)
 * @param cell route grid in blocks
 * @param sample blocks between the planner's samples along a step
 * @param roadSpeed speed on a road (at least)
 * @param margin how far beyond its two ends a route may stray, in blocks
 * @param maxNodes most grid cells one search opens
 * @param personCarry stacks a person carries
 * @param wagonCarry stacks a wagon carries
 * @param wagonSpeed what a wagon leaves of its steeds' speed (0-1)
 * @param bodies when parties are seen in person, and what is done when their bodies get stuck
 * @param tickInterval ticks between two moves of the parties
 * @param bridgeMaxLength longest bridge a marker accepts, in blocks
 * @param steedRadius steeds this close to the Hall can go with a party
 * @param greed how hard route planning leans toward the goal ({@link RoutePlanner.Settings#greed})
 */
public record JourneySettings(double walkSpeed, int cell, int sample, double roadSpeed, int margin, int maxNodes,
        int personCarry, int wagonCarry, double wagonSpeed, Bodies bodies, int tickInterval, int bridgeMaxLength,
        int steedRadius, double greed) {
    /**
     * @param showRadius a player this close to a party in a loaded chunk sees it in person
     * @param hideRadius bodies go back to being data once no player is this close
     * @param repathSeconds a party making no headway this long finds its way again
     * @param hopSeconds still none this long: it steps ahead to the next point on its way
     * @param giveUpSeconds still none: the bodies go and the party goes on as data
     */
    public record Bodies(int showRadius, int hideRadius, int repathSeconds, int hopSeconds, int giveUpSeconds) {
        public static final Bodies DEFAULT = new Bodies(96, 144, 8, 20, 45);
    }

    public static final JourneySettings DEFAULT = new JourneySettings(2.2, 32, 8, 1.6, 1024, 250000, 4, 27, 0.85,
            Bodies.DEFAULT, 20, 64, 48, 1.5);

    public RoutePlanner.Settings planner() {
        return new RoutePlanner.Settings(cell, sample, roadSpeed, margin, maxNodes, greed);
    }
}
