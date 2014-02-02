package icbm.sentry.turret.block;

import calclavia.lib.access.AccessProfile;
import calclavia.lib.access.IProfileContainer;
import calclavia.lib.multiblock.fake.IBlockActivate;
import calclavia.lib.network.PacketHandler;
import calclavia.lib.prefab.AutoServo;
import calclavia.lib.prefab.IGyroMotor;
import calclavia.lib.prefab.tile.IRotatable;
import calclavia.lib.terminal.TileTerminal;
import calclavia.lib.utility.inventory.ExternalInventory;
import calclavia.lib.utility.inventory.IExternalInventory;
import calclavia.lib.utility.inventory.IExternalInventoryBox;
import com.google.common.io.ByteArrayDataInput;
import icbm.core.ICBMCore;
import icbm.sentry.ICBMSentry;
import icbm.sentry.turret.EntitySentryFake;
import icbm.sentry.turret.Sentry;
import icbm.sentry.turret.SentryRegistry;
import icbm.sentry.turret.SentryTypes;
import icbm.sentry.turret.ai.LookHelper;
import icbm.sentry.turret.modules.mount.MountedSentry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.ChatMessageComponent;
import net.minecraftforge.common.ForgeDirection;
import universalelectricity.api.energy.EnergyStorageHandler;

/** @author Darkguardsman, tgame14 */
public class TileSentry extends TileTerminal implements IProfileContainer, IRotatable, IGyroMotor, IExternalInventory, IBlockActivate
{
    protected static final int ROTATION_PACKET_ID = 3;
    protected static final int SENTRY_TYPE_PACKET_ID = 4;
    protected static final int DESCRIPTION_PACKET_ID = 5;

    /** Profile that control access properties for users */
    protected AccessProfile accessProfile;

    /** Sentries inventory used for upgrades and ammo */
    protected IExternalInventoryBox inventory;

    /** Sentry instance used to define the visuals and weapons of the sentry */
    protected Sentry sentry;

    /** TURRET AIM & ROTATION HELPER */
    public LookHelper lookHelper;

    /** Yaw servo rotation */
    public AutoServo yawMotor;
    /** Pitch servo rotation */
    public AutoServo pitchMotor;

    private static float[] yawData = { 360F, 0F, 5F };
    private static float[] pitchData = { 35F, -35F, 5F };
    private String unlocalizedName = "";
    private String SaveManagerSentryKey;

    private SentryTypes ClientSentryType;
    public EntitySentryFake sentryEntity;

    public TileSentry()
    {
        super();
        this.inventory = new ExternalInventory(this, 8);
        this.energy = new EnergyStorageHandler(1000);
        this.ClientSentryType = SentryTypes.VOID;

    }

    @Override
    public void initiate ()
    {
        super.initiate();
        this.yawMotor = new AutoServo(yawData[0], yawData[1], yawData[2]);
        this.pitchMotor = new AutoServo(pitchData[0], pitchData[1], pitchData[2]);

    }

    @Override
    public void updateEntity ()
    {
        super.updateEntity();
        if (this.getSentry() != null)
            this.getSentry().updateLoop();

    }

    // TODO Move this to Sentry object update loop
    @Deprecated
    protected void mountableSentryLoop ()
    {
        boolean flag = false;
        if (this.hasWorldObj() && (this.sentryEntity == null || this.sentryEntity.isDead))
        {
            this.sentryEntity = new EntitySentryFake(this, true);
            this.worldObj.spawnEntityInWorld(this.sentryEntity);
            flag = true;
        }

        //TODO set up handling for non-player entities, low Priority
        if (flag && this.sentryEntity.riddenByEntity instanceof EntityPlayer)
        {
            EntityPlayer mountedPlayer = (EntityPlayer) this.sentryEntity.riddenByEntity;

            if (mountedPlayer.rotationPitch > this.getPitchServo().upperLimit())
            {
                mountedPlayer.rotationPitch = this.getPitchServo().upperLimit();
            }
            if (mountedPlayer.rotationPitch < this.getPitchServo().lowerLimit())
            {
                mountedPlayer.rotationPitch = this.getPitchServo().lowerLimit();
            }
            this.getPitchServo().setRotation(mountedPlayer.rotationPitch);
            this.getYawServo().setRotation(mountedPlayer.rotationYaw);
        }

    }

    //TODO Move to AutoSentry Update Loo
    @Deprecated
    protected void autoSentryLoop ()
    {

        float prevYaw = this.getYawServo().getRotation();
        float prevPitch = this.getPitchServo().getRotation();
        this.yawMotor.update();
        this.pitchMotor.update();
        if (prevYaw != this.getYawServo().getRotation() || prevPitch != this.getPitchServo().getRotation())
        {
            PacketHandler.sendPacketToClients(this.getRotationPacket());
        }
    }

    @Override
    public AccessProfile getAccessProfile ()
    {
        if (this.accessProfile == null)
        {
            this.setAccessProfile(new AccessProfile().generateNew("default", this));
        }
        return accessProfile;
    }

    @Override
    public void setAccessProfile (AccessProfile profile)
    {
        this.accessProfile = profile;
    }

    @Override
    public boolean canAccess (String username)
    {
        return accessProfile.getUserAccess(username) != null;
    }

    @Override
    public Packet getDescriptionPacket ()
    {
        Integer sentryOrdinal = SentryTypes.VOID.ordinal();
        if (this.getSentry() != null)
            sentryOrdinal = this.getSentry().getSentryType().ordinal();
        else
            ICBMSentry.LOGGER.warning("Failed to send Sentry data through Descriptive packet");

        return ICBMCore.PACKET_TILE.getPacketWithID(DESCRIPTION_PACKET_ID, this, sentryOrdinal, this.getYawServo().getRotation(), this.getPitchServo().getRotation());
    }

    public Packet getNBTPacket ()
    {
        NBTTagCompound tag = new NBTTagCompound();
        this.writeToNBT(tag);
        return ICBMCore.PACKET_TILE.getPacketWithID(NBT_PACKET_ID, this, tag);
    }

    @Override
    public Packet getTerminalPacket ()
    {
        return ICBMCore.PACKET_TILE.getPacketWithID(TERMINAL_PACKET_ID, this, this.getPacketData(1).toArray());
    }

    @Override
    public Packet getCommandPacket (String username, String cmdInput)
    {
        return ICBMCore.PACKET_TILE.getPacketWithID(COMMAND_PACKET_ID, this, username, cmdInput);
    }

    public Packet getRotationPacket ()
    {
        return ICBMCore.PACKET_TILE.getPacketWithID(ROTATION_PACKET_ID, this, this.getYawServo().getRotation(), this.getPitchServo().getRotation());
    }

    @Override
    public boolean onReceivePacket (int id, ByteArrayDataInput data, EntityPlayer player, Object... extra)
    {
        if (!super.onReceivePacket(id, data, player, extra))
        {
            if (id == DESCRIPTION_PACKET_ID)
            {

                this.ClientSentryType = SentryTypes.get(data.readInt());
                this.getYawServo().setRotation(data.readFloat());
                this.getPitchServo().setRotation(data.readFloat());
                return true;
            }
            if (id == ROTATION_PACKET_ID)
            {
                this.getYawServo().setRotation(data.readFloat());
                this.getPitchServo().setRotation(data.readFloat());
                return true;
            }

            return false;
        }
        return true;
    }

    @Override
    public ForgeDirection getDirection ()
    {
        return ForgeDirection.getOrientation(this.worldObj.getBlockMetadata(xCoord, yCoord, zCoord));
    }

    @Override
    public void setDirection (ForgeDirection direction)
    {
        this.worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, direction.ordinal(), 3);
    }

    @Override
    public void writeToNBT (NBTTagCompound nbt)
    {
        super.writeToNBT(nbt);
        this.getInventory().save(nbt);
        if (this.getSentry() != null)
            this.getSentry().save(nbt);

        nbt.setString("unlocalizedName", this.unlocalizedName);
    }

    @Override
    public void readFromNBT (NBTTagCompound nbt)
    {
        super.readFromNBT(nbt);
        this.getInventory().load(nbt);
        NBTTagCompound tag = nbt.getCompoundTag("sentryTile");
        this.SaveManagerSentryKey = tag.getString("id");
        if (this.getSentry() != null)
            this.getSentry().load(nbt);

        this.unlocalizedName = nbt.getString("unlocalizedName");
        this.ClientSentryType = SentryTypes.get(this.unlocalizedName);
    }

    public SentryTypes getClientSentryType ()
    {
        return this.ClientSentryType;
    }

    @Override
    public IExternalInventoryBox getInventory ()
    {
        return this.inventory;
    }

    @Override
    public boolean canStore (ItemStack stack, int slot, ForgeDirection side)
    {
        return false;
    }

    @Override
    public boolean canRemove (ItemStack stack, int slot, ForgeDirection side)
    {
        return false;
    }

    @Override
    public AutoServo getYawServo ()
    {
        if (this.yawMotor == null)
            this.yawMotor = new AutoServo(yawData[0], yawData[1], yawData[2]);
        return this.yawMotor;
    }

    @Override
    public AutoServo getPitchServo ()
    {
        if (this.pitchMotor == null)
            this.pitchMotor = new AutoServo(pitchData[0], pitchData[1], pitchData[2]);
        return this.pitchMotor;
    }

    public Sentry getSentry ()
    {
        if (this.sentry == null)
            this.sentry = SentryRegistry.constructSentry(this.SaveManagerSentryKey, this);
        return this.sentry;
    }

    @Override
    public boolean onActivated (EntityPlayer entityPlayer)
    {
        if (entityPlayer != null)
        {
            entityPlayer.sendChatToPlayer(ChatMessageComponent.createFromText("Sentries are indev and don't currently have a functioning GUI"));

            if (!entityPlayer.isSneaking())
            {
                if (this.getSentry() instanceof MountedSentry && this.sentryEntity != null)
                {
                    if (this.sentryEntity.riddenByEntity instanceof EntityPlayer)
                    {
                        if (!this.worldObj.isRemote)
                        {
                            PacketHandler.sendPacketToClients(this.getRotationPacket());
                        }
                        return true;
                    }
                    this.mount(entityPlayer);
                }

            }

            return true;
        }
        return false;
    }

    public void mount (EntityPlayer entityPlayer)
    {
        if (!this.worldObj.isRemote)
        {
            entityPlayer.rotationYaw = this.getYawServo().getRotation();
            entityPlayer.rotationPitch = this.getPitchServo().getRotation();
            entityPlayer.mountEntity(this.sentryEntity);

        }

    }

    public EntitySentryFake getFakeEntity ()
    {
        return this.sentryEntity;
    }

    public void setFakeEntity (EntitySentryFake entitySentryFake)
    {
        this.sentryEntity = entitySentryFake;
    }

}
