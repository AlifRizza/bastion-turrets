package dev.bastion.client.compat.jade;

import dev.bastion.Bastion;
import dev.bastion.client.overwatch.OverwatchClient;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * No Jade popup under Overwatch (0.3.0 review): the view is the turret's, so whatever the body at the hub or the camera
 * looks at is not what the player means. Jade finds this class by its annotation; nothing else refers to it.
 */
@WailaPlugin(Bastion.MOD_ID)
public class OverwatchJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> OverwatchClient.active() ? null : accessor);
    }
}
