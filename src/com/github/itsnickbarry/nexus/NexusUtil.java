package com.github.itsnickbarry.nexus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;

public class NexusUtil {
    
    /*
     * Configuration information
     */
    //half-life, in days, of power and spread points; must be > 0 (or we can let 0 mean no decay)
    static long powerPointsHalfLife;
    static long spreadPointsHalfLife; 
    
    static double influenceMin = 1; //not currently configurable; I don't know what happens when it's changed
    
    static double powerLevelFactor;
    static int powerPointsBase; //number of points granted to a new Nexus
    static int powerPointsMin; //the number of points at which a Nexus is destroyed; 0 < powerPointsMin < powerPointsBase
    static double spreadLevelFactor; //how effective spreadPoints are as spreadPoints approaches 0; 0 <= spreadLevelFactor <= 1
    static double spreadLevelVariability; //this represents the possible deviation from normalizedSpread; 0 <= spreadLevelVariability <= 1
    static int maxRadius = 200;
    
    
    static boolean useSpheres;
    /*
     * 
     */

    
    static AtomicInteger nexusCurrentId = new AtomicInteger();
    static AtomicInteger ownerCurrentId = new AtomicInteger();

    static Map<Integer, Object> nexusOwners = new HashMap<Integer, Object>();
    
    //static Set<NexusOwner> nexusOwners = new HashSet<NexusOwner>();
    
    static List<Nexus> allNexus = new ArrayList<Nexus>(); // we might not even need this list; just use one of the sets

    static Set<Nexus> xmax = new TreeSet<Nexus>(new NexusComparator.XMax()); // sorted by max x
    static Set<Nexus> xmin = new TreeSet<Nexus>(new NexusComparator.XMin()); // sorted by min x
    static Set<Nexus> zmax = new TreeSet<Nexus>(new NexusComparator.ZMax()); // sorted by max z
    static Set<Nexus> zmin = new TreeSet<Nexus>(new NexusComparator.ZMin()); // sorted by min z
    
    static Map<SimpleXYZ, Nexus> nexusByXYZ = new HashMap<SimpleXYZ, Nexus>();
    static Map<SimpleXZ, Set<Nexus>> nexusByChunk = new HashMap<SimpleXZ, Set<Nexus>>();
    
    // Is instanceof UUID or instanceof NexusGroup
    public static Object getNexusOwner(int id) {
        return nexusOwners.get(id);
    }
    
    public static void addNexusGroup(NexusGroup group) {
        nexusOwners.put(ownerCurrentId.incrementAndGet(), group);
    }
    
    public static Set<NexusGroup> getNexusGroups() {
        Set<NexusGroup> matches = new HashSet<NexusGroup>();
        for (Object object :nexusOwners.values()) {
            if (object instanceof NexusGroup) {
                matches.add((NexusGroup) object);
            }
        }
        return matches;
    }
    
    public static int absoluteGetPlayerId(UUID playerUID) {
        Set<Entry<Integer, Object>> entries = nexusOwners.entrySet();
        for (Entry<Integer, Object> entry : entries) {
            if (entry.getValue().equals(playerUID)) {
                System.out.println("Player already registered");
                return entry.getKey();
            }
        }
        System.out.println("Player now registered");
        int id = ownerCurrentId.incrementAndGet();
        nexusOwners.put(id, playerUID);
        return id;
    }
    
    /*
    public static NexusOwner getNexusOwner(int id) {
    	for (NexusOwner nexusOwner : nexusOwners) {
    		if (nexusOwner.getId() == id)
    			return nexusOwner;
    	}
    	return null;
    }
    
    public static void addNexusOwner(NexusOwner owner) {
    	nexusOwners.add(owner);
    }
    
    public static NexusPlayer absoluteGetNexusPlayer(UUID playerUID) {
        if (nexusOwners.contains(playerUID)) {
            for (NexusOwner nexusOwner : nexusOwners) {
                if (nexusOwner.equals(playerUID)) {
                    System.out.println("Returned pre-existing nexusOwner");
                    return (NexusPlayer) nexusOwner;
                }
            }
            return null;
        } else {
            NexusPlayer newNexusPlayer = new NexusPlayer(playerUID);
            nexusOwners.add(newNexusPlayer);
            System.out.println("Returned new nexusOwner");
            return newNexusPlayer;
        }
    }
    
    public static NexusPlayer getNexusPlayer(UUID playerUID) {
    	for (NexusOwner nexusOwner : nexusOwners) {
    		if (nexusOwner.equals(playerUID))
    			return (NexusPlayer) nexusOwner;
    	}
    	return null;
    }
    */
    
    public static void addNexus2(Nexus nexus) {
        nexusByXYZ.put(nexus.getXYZ(), nexus);
        Set<Nexus> nexusInChunk = nexusByChunk.get(nexus.getChunkCoordinates());
        if (nexusInChunk != null) {
            nexusInChunk.add(nexus);
        } else {
            Set<Nexus> mappedSet = new LinkedHashSet<Nexus>();
            mappedSet.add(nexus);
            nexusByChunk.put(nexus.getChunkCoordinates(), mappedSet);
        }
        refreshSets2();
    }
    
    public static Nexus determineBlockOwner2(Block block) {
        //long start = System.currentTimeMillis();
        SimpleXZ blockChunk = new SimpleXZ(block.getChunk());
        int chunkRadius = (int) Math.ceil(maxRadius / 16);
        Set<Nexus> possibleAffected = new LinkedHashSet<Nexus>();
        for (int i = -chunkRadius; i <= chunkRadius; i ++) {
            for (int j = -chunkRadius; j <= chunkRadius; j ++) {
                SimpleXZ current = new SimpleXZ(blockChunk.getX() + i, blockChunk.getZ() + j);
                if (nexusByChunk.containsKey(current))
                    possibleAffected.addAll(nexusByChunk.get(current));
            }
        }
        double highestInfluence = influenceMin;
        Nexus strongestNexus = null;
        for (Nexus n : possibleAffected) {
            double influence = n.influenceAt(block);
            if (influence > highestInfluence) {
                highestInfluence = influence;
                strongestNexus = n;
            }
        }
        //System.out.println("Search: " + (System.currentTimeMillis() - start));
        return strongestNexus;
    }
    
    public static void refreshSets2() {
        for (Nexus n : nexusByXYZ.values())
            n.update();
    }
    
    public static void addNexus(Nexus nexus) {
        allNexus.add(nexus);
        refreshSets();
    }

    public static Nexus determineBlockOwner(Block block) {

        Nexus point = new Nexus(block, null, false);

        Set<Nexus> candidates = new TreeSet<Nexus>(new NexusComparator.XMax());

        candidates.addAll(((TreeSet<Nexus>) xmax).tailSet(point, true));
        candidates.retainAll(((TreeSet<Nexus>) xmin).headSet(point, true));
        candidates.retainAll(((TreeSet<Nexus>) zmax).tailSet(point, true));
        candidates.retainAll(((TreeSet<Nexus>) zmin).headSet(point, true));

        double bestInfluence = influenceMin;
        Nexus bestNexus = null;

        for (Nexus n : candidates) {
        	//TODO maybe find a better way to check for world
        	if (point.getWorldUID() != n.getWorldUID())
        		continue;
            double influence = n.influenceAt(block);
            if (influence > bestInfluence) {
                bestInfluence = influence;
                bestNexus = n;
            }
        }

        return bestNexus;
    }
    
    public static void loadConfig() {
        FileConfiguration config = Bukkit.getPluginManager().getPlugin("Nexus").getConfig();
        powerPointsHalfLife = (long)(config.getDouble("powerPointsHalfLife") * 8.64e7);
        spreadPointsHalfLife = (long)(config.getDouble("spreadPointsHalfLife") * 8.64e7);
        powerLevelFactor = config.getDouble("powerLevelFactor");
        powerPointsBase = config.getInt("powerPointsBase");
        powerPointsMin = config.getInt("powerPointsMin");
        spreadLevelFactor = config.getDouble("spreadLevelFactor");
        spreadLevelVariability = config.getDouble("spreadLevelVariability");
        useSpheres = config.getBoolean("useSpheres");
        
        //TODO check that all values are within appropriate ranges and, if they're not, revert to defaults
    }

    public static void refreshSets() {
        // do this onEnable, whenever Nexus points are scheduled to be assigned, and whenever a Nexus is created or destroyed

        List<Nexus> decayedNexus = new ArrayList<Nexus>();
        
        for (Nexus n : allNexus) {
            if (!n.update()) {
                decayedNexus.add(n);
            }
        }
        
        allNexus.removeAll(decayedNexus);

        xmax.addAll(allNexus);
        xmax.removeAll(decayedNexus);
        xmin.addAll(allNexus);
        xmin.removeAll(decayedNexus);
        zmax.addAll(allNexus);
        zmax.removeAll(decayedNexus);
        zmin.addAll(allNexus);
        zmin.removeAll(decayedNexus);
    }
}