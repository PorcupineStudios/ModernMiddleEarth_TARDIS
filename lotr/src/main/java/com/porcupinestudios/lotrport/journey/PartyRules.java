package com.porcupinestudios.lotrport.journey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How fast a party goes and how much it carries (plain rules). Everyone needs a colonist; steeds and wagons are
 * extras. Wagons are hitched to steeds that can draw one ({@code draws_wagon}), the strongest first; the other steeds
 * are ridden, a person each, or led as pack animals. The party moves at its slowest: anyone on foot sets walking pace
 * (1), else the slowest ridden steed; wagons slow their steeds by {@code wagonSpeed}. Every steed carries its load,
 * every wagon its own, every person a little.
 */
public final class PartyRules {
    private PartyRules() {}

    /** A steed in a party: its speed against walking (1) and what it carries, in stacks. */
    public record Steed(double speed, int carry, boolean drawsWagon) {}

    /**
     * What a party can do.
     *
     * @param ok false when there are more wagons than steeds to draw them, or nobody to lead
     * @param speed against walking pace (1)
     * @param stacks how many stacks of goods it carries
     * @param why the reason when not ok
     */
    public record Result(boolean ok, double speed, int stacks, String why) {}

    public static Result of(int people, List<Steed> steeds, int wagons, double wagonSpeed, int personCarry,
            int wagonCarry) {
        if (people <= 0) {
            return new Result(false, 0, 0, "nobody");
        }
        List<Steed> draft = new ArrayList<>();
        List<Steed> rest = new ArrayList<>();
        // the strongest drawers pull the wagons
        List<Steed> sorted = new ArrayList<>(steeds);
        sorted.sort(Comparator.comparingDouble(Steed::speed).reversed());
        for (Steed st : sorted) {
            if (st.drawsWagon() && draft.size() < wagons) {
                draft.add(st);
            } else {
                rest.add(st);
            }
        }
        if (draft.size() < wagons) {
            return new Result(false, 0, 0, "wagons");
        }
        // ridden: the fastest steeds left, one per person; the others are led
        rest.sort(Comparator.comparingDouble(Steed::speed).reversed());
        double speed;
        if (rest.size() < people) {
            speed = 1;
        } else {
            speed = Double.MAX_VALUE;
            for (int i = 0; i < people; i++) {
                speed = Math.min(speed, rest.get(i).speed());
            }
            // a led steed slower than the riders holds them back
            for (int i = people; i < rest.size(); i++) {
                speed = Math.min(speed, Math.max(1, rest.get(i).speed()));
            }
        }
        if (wagons > 0) {
            double slowest = draft.stream().mapToDouble(Steed::speed).min().orElse(1);
            speed = Math.min(speed, slowest * wagonSpeed);
        }
        int stacks = people * personCarry + wagons * wagonCarry;
        for (Steed st : steeds) {
            stacks += st.carry();
        }
        return new Result(true, Math.max(0.1, speed), stacks, "");
    }
}
