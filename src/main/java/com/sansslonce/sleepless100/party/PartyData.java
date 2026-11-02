package com.sansslonce.sleepless100.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import com.sansslonce.sleepless100.SleeplessNights;
import com.sansslonce.sleepless100.core.PartyRules;
import com.sansslonce.sleepless100.core.PartyRules.MobPlan;

/** Friday Night Party state saved with the world (data/sleepless100_party.dat), so a restart resumes the party. */
public class PartyData extends WorldSavedData {

    public static final String NAME = SleeplessNights.MODID + "_party";
    private static final int TAG_COMPOUND = 10;

    /** Difficulty day of the last party that started, so each Friday has at most one. */
    public long lastPartyDay = -1;
    /** The running party, or null. */
    public Party party;
    /** Pending block repairs, keyed by position. */
    public final Map<Long, Repair> repairs = new LinkedHashMap<>();

    public PartyData(String name) {
        super(name);
    }

    public static PartyData get(World world) {
        MapStorage storage = world.mapStorage;
        PartyData data = (PartyData) storage.loadData(PartyData.class, NAME);
        if (data == null) {
            data = new PartyData(NAME);
            storage.setData(NAME, data);
        }
        return data;
    }

    public static final class Party {

        public long id; // world time at the start; party mobs carry it
        public int friday;
        public int size;
        public long endTime;
        public double tx, ty, tz;
        public UUID host;
        public final Set<UUID> guests = new HashSet<>();
        public final Set<UUID> fallen = new HashSet<>();
        public boolean attacking;
        /** Started during the party night (not by command in daytime): ends when the night is over. */
        public boolean nightStart;
        public long nextSpawnTime;
        public double baseAngle;
        public final List<Group> groups = new ArrayList<>();
        public final List<int[]> webs = new ArrayList<>();

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("id", id);
            t.setInteger("friday", friday);
            t.setInteger("size", size);
            t.setLong("end", endTime);
            t.setDouble("tx", tx);
            t.setDouble("ty", ty);
            t.setDouble("tz", tz);
            if (host != null) {
                t.setString("host", host.toString());
            }
            t.setTag("guests", uuids(guests));
            t.setTag("fallen", uuids(fallen));
            t.setBoolean("attacking", attacking);
            t.setBoolean("nightStart", nightStart);
            t.setLong("nextSpawn", nextSpawnTime);
            t.setDouble("angle", baseAngle);
            NBTTagList gl = new NBTTagList();
            for (Group g : groups) {
                gl.appendTag(g.write());
            }
            t.setTag("groups", gl);
            NBTTagList wl = new NBTTagList();
            for (int[] w : webs) {
                NBTTagCompound p = new NBTTagCompound();
                p.setIntArray("p", w);
                wl.appendTag(p);
            }
            t.setTag("webs", wl);
            return t;
        }

        static Party read(NBTTagCompound t) {
            Party p = new Party();
            p.id = t.getLong("id");
            p.friday = t.getInteger("friday");
            p.size = t.getInteger("size");
            p.endTime = t.getLong("end");
            p.tx = t.getDouble("tx");
            p.ty = t.getDouble("ty");
            p.tz = t.getDouble("tz");
            p.host = t.hasKey("host") ? UUID.fromString(t.getString("host")) : null;
            readUuids(t.getTagList("guests", 8), p.guests);
            readUuids(t.getTagList("fallen", 8), p.fallen);
            p.attacking = t.getBoolean("attacking");
            p.nightStart = t.getBoolean("nightStart");
            p.nextSpawnTime = t.getLong("nextSpawn");
            p.baseAngle = t.getDouble("angle");
            NBTTagList gl = t.getTagList("groups", TAG_COMPOUND);
            for (int i = 0; i < gl.tagCount(); i++) {
                p.groups.add(Group.read(gl.getCompoundTagAt(i)));
            }
            NBTTagList wl = t.getTagList("webs", TAG_COMPOUND);
            for (int i = 0; i < wl.tagCount(); i++) {
                int[] w = wl.getCompoundTagAt(i)
                    .getIntArray("p");
                if (w.length == 3) {
                    p.webs.add(w);
                }
            }
            return p;
        }

        public boolean allGroupsDone() {
            for (Group g : groups) {
                if (!g.spawned && !g.abandoned) {
                    return false;
                }
            }
            return true;
        }
    }

    public static final class Group {

        public int index;
        public double angle;
        public final List<MobPlan> plans = new ArrayList<>();
        public boolean spawned, abandoned;
        public double sx, sy, sz; // spawn spot
        public double rx, ry, rz; // rally point

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("index", index);
            t.setDouble("angle", angle);
            NBTTagList pl = new NBTTagList();
            for (MobPlan m : plans) {
                NBTTagCompound mt = new NBTTagCompound();
                mt.setString("type", m.type.name());
                mt.setString("role", m.role.name());
                mt.setString("powers", joinPowers(m.powers));
                pl.appendTag(mt);
            }
            t.setTag("plans", pl);
            t.setBoolean("spawned", spawned);
            t.setBoolean("abandoned", abandoned);
            t.setDouble("sx", sx);
            t.setDouble("sy", sy);
            t.setDouble("sz", sz);
            t.setDouble("rx", rx);
            t.setDouble("ry", ry);
            t.setDouble("rz", rz);
            return t;
        }

        static Group read(NBTTagCompound t) {
            Group g = new Group();
            g.index = t.getInteger("index");
            g.angle = t.getDouble("angle");
            NBTTagList pl = t.getTagList("plans", TAG_COMPOUND);
            for (int i = 0; i < pl.tagCount(); i++) {
                NBTTagCompound mt = pl.getCompoundTagAt(i);
                try {
                    g.plans.add(
                        new MobPlan(
                            PartyRules.MobType.valueOf(mt.getString("type")),
                            PartyRules.Role.valueOf(mt.getString("role")),
                            parsePowers(mt.getString("powers"))));
                } catch (IllegalArgumentException e) {
                    // unknown entry from another version: skip it
                }
            }
            g.spawned = t.getBoolean("spawned");
            g.abandoned = t.getBoolean("abandoned");
            g.sx = t.getDouble("sx");
            g.sy = t.getDouble("sy");
            g.sz = t.getDouble("sz");
            g.rx = t.getDouble("rx");
            g.ry = t.getDouble("ry");
            g.rz = t.getDouble("rz");
            return g;
        }
    }

    /** A block changed by a party mob, to put back later if it's still as the mob left it. */
    public static final class Repair {

        public final int x, y, z;
        public final Block original;
        public final int originalMeta;
        public Block placed;
        public int placedMeta;
        public long due;

        Repair(int x, int y, int z, Block original, int originalMeta, Block placed, int placedMeta, long due) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.original = original;
            this.originalMeta = originalMeta;
            this.placed = placed;
            this.placedMeta = placedMeta;
            this.due = due;
        }
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Records a change; a second change at the same spot keeps the first original and resets the timer. */
    public void recordRepair(int x, int y, int z, Block original, int originalMeta, Block placed, int placedMeta,
        long due) {
        long k = key(x, y, z);
        Repair existing = repairs.remove(k);
        if (existing != null) {
            existing.placed = placed;
            existing.placedMeta = placedMeta;
            existing.due = due;
            repairs.put(k, existing);
        } else {
            repairs.put(k, new Repair(x, y, z, original, originalMeta, placed, placedMeta, due));
        }
        markDirty();
    }

    public List<Repair> takeDueRepairs(long now, int max) {
        List<Repair> due = new ArrayList<>();
        Iterator<Repair> it = repairs.values()
            .iterator();
        while (it.hasNext() && due.size() < max) {
            Repair r = it.next();
            if (r.due > now) {
                break; // kept in time order
            }
            due.add(r);
            it.remove();
        }
        if (!due.isEmpty()) {
            markDirty();
        }
        return due;
    }

    public void putBack(Repair r) {
        repairs.put(key(r.x, r.y, r.z), r);
    }

    static String joinPowers(List<PartyRules.Power> powers) {
        StringBuilder b = new StringBuilder();
        for (PartyRules.Power p : powers) {
            if (b.length() > 0) {
                b.append(',');
            }
            b.append(p.name());
        }
        return b.toString();
    }

    static List<PartyRules.Power> parsePowers(String s) {
        if (s == null || s.isEmpty()) {
            return Collections.emptyList();
        }
        List<PartyRules.Power> out = new ArrayList<>();
        for (String part : s.split(",")) {
            try {
                out.add(PartyRules.Power.valueOf(part));
            } catch (IllegalArgumentException e) {
                // skip unknown
            }
        }
        return out;
    }

    private static NBTTagList uuids(Set<UUID> set) {
        NBTTagList l = new NBTTagList();
        for (UUID u : set) {
            l.appendTag(new net.minecraft.nbt.NBTTagString(u.toString()));
        }
        return l;
    }

    private static void readUuids(NBTTagList l, Set<UUID> into) {
        for (int i = 0; i < l.tagCount(); i++) {
            try {
                into.add(UUID.fromString(l.getStringTagAt(i)));
            } catch (IllegalArgumentException e) {
                // skip
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        lastPartyDay = tag.hasKey("lastPartyDay") ? tag.getLong("lastPartyDay") : -1;
        party = tag.hasKey("party") ? Party.read(tag.getCompoundTag("party")) : null;
        repairs.clear();
        NBTTagList rl = tag.getTagList("repairs", TAG_COMPOUND);
        for (int i = 0; i < rl.tagCount(); i++) {
            NBTTagCompound t = rl.getCompoundTagAt(i);
            Block original = Block.getBlockFromName(t.getString("orig"));
            Block placed = Block.getBlockFromName(t.getString("placed"));
            if (original != null && placed != null) {
                int x = t.getInteger("x"), y = t.getInteger("y"), z = t.getInteger("z");
                repairs.put(
                    key(x, y, z),
                    new Repair(
                        x,
                        y,
                        z,
                        original,
                        t.getInteger("origMeta"),
                        placed,
                        t.getInteger("placedMeta"),
                        t.getLong("due")));
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setLong("lastPartyDay", lastPartyDay);
        if (party != null) {
            tag.setTag("party", party.write());
        }
        NBTTagList rl = new NBTTagList();
        for (Repair r : repairs.values()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("x", r.x);
            t.setInteger("y", r.y);
            t.setInteger("z", r.z);
            t.setString("orig", Block.blockRegistry.getNameForObject(r.original));
            t.setInteger("origMeta", r.originalMeta);
            t.setString("placed", Block.blockRegistry.getNameForObject(r.placed));
            t.setInteger("placedMeta", r.placedMeta);
            t.setLong("due", r.due);
            rl.appendTag(t);
        }
        tag.setTag("repairs", rl);
    }
}
