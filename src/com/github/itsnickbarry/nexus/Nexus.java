package com.github.itsnickbarry.nexus;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class Nexus {

    private final int id;
    private NexusOwner owner;
    
    private final int x, y, z;
    private final UUID worldUID;
    
    private int power, spread, radius; // These values need to be stored here and updated regularly
    
    private int powerPoints, spreadPoints;
    private long lastDecayTime;

    public Nexus(Block block, NexusOwner owner, boolean real) {
        this.x = block.getX();
        this.y = block.getY();
        this.z = block.getZ();
        this.worldUID = block.getWorld().getUID();
        this.powerPoints = NexusUtil.powerPointsBase;
        if (real) {
            this.id = NexusUtil.nexusCurrentId.incrementAndGet();
            this.owner = owner;
            NexusUtil.refreshSets();
        } else {
            this.id = 0;
            this.owner = null;
            this.radius = 0;
        }
    }

    public NexusOwner getOwner() {
    	return this.owner;
    }
    
    // TODO Improve
    public void setOwner(NexusOwner owner) {
    	this.owner = owner;
    }
    
    public int getRadius() {
        return this.radius;
    }

    public int getId() {
        return this.id;
    }

    public int getPower() {
        return this.power;
    }

    public int getSpread() {
        return this.spread;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }
    
    public UUID getWorldUID() {
        return this.worldUID;
    }
    
    public boolean allowsPlayerBlockEdit(Player p) {
        if (this.owner.equals(p))
        	return true;
        if (this.owner instanceof NexusGroup) {
        	NexusGroup owningGroup = (NexusGroup) this.owner;
        	if (owningGroup.getRole(p.getUniqueId()) != NexusGroup.Role.NONE)
        		return true;
        }
        return false;
    }
    
    public double powerAt(Block block) {
        //function is not continuous when spread == 0; in this case, return 0 (the only situation in which spread == 0 should be when power == 0)
        int x = block.getX();
        int y = (NexusUtil.useSpheres ? block.getY() : this.y);
        int z = block.getZ();
        double distance = Math.sqrt(Math.pow(x - this.getX(), 2) + Math.pow(y - this.getY(), 2) + Math.pow(z - this.getZ(), 2));
        return this.spread == 0 ? 0 :
            (this.power / this.spread)
                * Math.exp(-1
                        * ((Math.pow(distance, 2)) / (2 * Math.pow(this.spread, 2))));
    }
    
    /**
     * 
     * @return true if Nexus still has enough power points to remain active
     */
    public boolean update() {
        //decayPoints() could potentially be run at a different time, but will be run here for now
        if (!this.decayPoints()){
            return false;
        }
        this.calculatePower();
        this.calculateSpread();
        this.calculateRadius();
        
        return true;
    }
    
    private void calculatePower() {
        //TODO this increases too quickly at the beginning; maybe a linear function would work, in combination with half-life or exponential decay of powerPoints
        this.power =  (int) (Math.sqrt((double)NexusUtil.powerLevelFactor * (double)this.powerPoints));
    }

    private void calculateRadius() {
        //function is not continuous when power < spread; in this case, set radius to 0
        this.radius = this.power < this.spread ?  0 : (int) Math.sqrt(-2 * Math.pow((double)this.spread, 2) * Math.log(((double)NexusUtil.powerLevelMin * (double)this.spread) / (double)this.power));
    }
    
    private void calculateSpread() {
        //currently limited to normalizedSpread +- normalizedSpread
        double normalizedSpread = calculateSpreadNormalized();
        
        this.spread =(int) (normalizedSpread + ((2 / Math.PI) * normalizedSpread * NexusUtil.spreadLevelVariability * Math.atan(NexusUtil.spreadLevelFactor * (double)this.spreadPoints)));
    }
    
    /**
     * 
     * @return the spread level of a Nexus at a given power level, before accounting for spread points
     */
    private double calculateSpreadNormalized() {
        //This formula is only partially arbitrary.  I think I could have gotten to it logically if I knew how to do math.
        return Math.PI * Math.sqrt((double)this.power / Math.PI);
    }
    
    /**
     * 
     * @return true if Nexus still has enough power points to remain active
     */
    private boolean decayPoints() {
        if (NexusUtil.powerPointsHalfLife == 0)
            this.powerPoints = (int) (this.powerPoints * Math.pow(.5, TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - this.lastDecayTime) / NexusUtil.powerPointsHalfLife));
        if (NexusUtil.spreadPointsHalfLife == 0)
            this.spreadPoints = (int) (this.spreadPoints * Math.pow(.5, TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - this.lastDecayTime) / NexusUtil.spreadPointsHalfLife));
        return this.powerPoints > NexusUtil.powerPointsMin;
    }
}