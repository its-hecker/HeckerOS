// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

import java.io.IOException;
import java.util.*;

/** A small, journaled owner of hotspot block-list entries, never arbitrary firewall rules. */
public final class NetLabPolicy {
    public static final long MAX_DURATION = 24 * 60 * 60 * 1000L;
    public static final int MAX_RULES = 64;
    public static final class Rule {
        public final String mac;
        public final long untilWall, untilElapsed, createdElapsed;
        public String state;
        public Rule(String mac, long wall, long elapsed, long created, String state) {
            this.mac = normalize(mac); untilWall = wall; untilElapsed = elapsed;
            createdElapsed = created; this.state = state;
        }
        public Rule copy() { return new Rule(mac, untilWall, untilElapsed, createdElapsed, state); }
    }
    public interface Backend {
        boolean active();
        boolean canBlock();
        boolean canSchedule();
        Set<String> clients() throws IOException;
        Set<String> blocked() throws IOException;
        Set<String> allowed() throws IOException;
        /** Must preserve the rest of the native hotspot configuration and read back the result. */
        void writeBlocked(Set<String> before, Set<String> updated) throws IOException;
        long wall();
        long elapsed();
    }
    public interface Journal { void save(Collection<Rule> rules) throws IOException; }
    public static final class Failure extends Exception {
        public final String code;
        public Failure(String code) { super(code); this.code = code; }
    }
    private final Backend backend;
    private final Journal journal;
    private final LinkedHashMap<String, Rule> owned = new LinkedHashMap<>();

    public NetLabPolicy(Backend backend, Journal journal, Collection<Rule> saved) {
        this.backend = backend; this.journal = journal;
        if (saved.size() > MAX_RULES) throw new IllegalArgumentException("journal_size");
        for (Rule r : saved) {
            if (owned.put(r.mac, r.copy()) != null) throw new IllegalArgumentException("duplicate_rule");
        }
    }
    public static String normalize(String value) {
        if (value == null || !value.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))
            throw new IllegalArgumentException("invalid_mac");
        String mac = value.toLowerCase(Locale.ROOT);
        if ((Integer.parseInt(mac.substring(0, 2), 16) & 1) != 0 || mac.equals("00:00:00:00:00:00"))
            throw new IllegalArgumentException("invalid_mac");
        return mac;
    }
    public List<Rule> rules() {
        ArrayList<Rule> result = new ArrayList<>();
        for (Rule r : owned.values()) result.add(r.copy());
        return result;
    }
    public void pause(String address, long duration) throws Failure {
        final String mac;
        try { mac = normalize(address); } catch (IllegalArgumentException e) { throw new Failure("invalid_mac"); }
        if (!backend.active()) throw new Failure("hotspot_off");
        if (!backend.canBlock()) throw new Failure("hal_unsupported");
        if (duration < 0 || duration > MAX_DURATION) throw new Failure("invalid_duration");
        if (duration > 0 && !backend.canSchedule()) throw new Failure("timer_unavailable");
        if (owned.containsKey(mac)) throw new Failure("already_owned");
        if (owned.size() >= MAX_RULES) throw new Failure("rule_limit");
        try {
            if (!backend.clients().contains(mac)) throw new Failure("not_connected_client");
            Set<String> blocked = new LinkedHashSet<>(backend.blocked());
            if (blocked.contains(mac) || backend.allowed().contains(mac)) throw new Failure("external_policy");
            Rule rule = new Rule(mac, duration == 0 ? 0 : backend.wall() + duration,
                    duration == 0 ? 0 : backend.elapsed() + duration, backend.elapsed(), "applying");
            owned.put(mac, rule);
            try { journal.save(rules()); }
            catch (IOException failure) { owned.remove(mac); throw new Failure("journal_failed"); }
            // Ownership is durable before native Wi-Fi is asked to change anything.
            Set<String> before = new LinkedHashSet<>(blocked); blocked.add(mac);
            try {
                backend.writeBlocked(before, blocked);
                if (!backend.blocked().contains(mac)) throw new IOException("readback");
                rule.state = backend.clients().contains(mac) ? "applying" : "enforced";
                journal.save(rules());
            } catch (IOException failure) {
                rule.state = "failed";
                // Keep the journal until verified rollback, including an ambiguous native result.
                try { restore(mac); } catch (Failure rollbackPending) { /* Retried by the owner. */ }
                throw new Failure("native_failed");
            }
        } catch (IOException unavailable) { throw new Failure("native_unavailable"); }
    }
    public void restore(String address) throws Failure {
        final String mac;
        try { mac = normalize(address); } catch (IllegalArgumentException e) { throw new Failure("invalid_mac"); }
        Rule rule = owned.get(mac);
        if (rule == null) throw new Failure("not_owned");
        restoreSet(Collections.singleton(mac));
    }
    public void restoreAll() throws Failure { restoreSet(new LinkedHashSet<>(owned.keySet())); }
    private void restoreSet(Set<String> selected) throws Failure {
        if (selected.isEmpty()) return;
        for (String mac : selected) owned.get(mac).state = "restoring";
        try {
            // Also works with the hotspot off; no capability override is needed to remove a rule.
            Set<String> current = new LinkedHashSet<>(backend.blocked());
            Set<String> updated = new LinkedHashSet<>(current); updated.removeAll(selected);
            if (!current.equals(updated)) backend.writeBlocked(current, updated);
            if (!Collections.disjoint(backend.blocked(), selected)) throw new IOException("readback");
            LinkedHashMap<String, Rule> before = new LinkedHashMap<>(owned);
            for (String mac : selected) owned.remove(mac);
            try { journal.save(rules()); }
            catch (IOException failure) { owned.clear(); owned.putAll(before); throw failure; }
        } catch (IOException unavailable) { throw new Failure("restore_pending"); }
    }
    /** Invoked on native callbacks and a bounded maintenance timer. */
    public void maintain() throws Failure {
        if (owned.isEmpty()) return;
        Set<String> blocked, clients;
        try { blocked = backend.blocked(); clients = backend.clients(); }
        catch (IOException unavailable) { throw new Failure("native_unavailable"); }
        Set<String> restore = new LinkedHashSet<>();
        for (Rule r : owned.values()) {
            if (!backend.active() || r.state.equals("restoring") || r.state.equals("failed")
                    || r.untilWall > 0 && (backend.wall() >= r.untilWall || backend.elapsed() >= r.untilElapsed)
                    || r.state.equals("applying") && backend.elapsed() - r.createdElapsed >= 15_000) {
                restore.add(r.mac); continue;
            }
            // A changed Wi-Fi configuration can remove an entry outside NetLab.
            if (!blocked.contains(r.mac)) { restore.add(r.mac); continue; }
            r.state = clients.contains(r.mac) ? "applying" : "enforced";
        }
        restoreSet(restore);
    }
}
