package com.example.addon;

import com.example.addon.modules.PlayerTeleport;
import com.example.addon.modules.BasePlace;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dune extends MeteorAddon {
        public static final Logger LOG = LoggerFactory.getLogger(Dune.class);
        public static final Category Main = new Category("Dune", Items.AMETHYST_SHARD.getDefaultStack());


        @Override
        public void onInitialize() {
                LOG.info("Initializing Dune");
                Modules.get().add(new PlayerTeleport());
                Modules.get().add(new BasePlace());

        }

        @Override
        public void onRegisterCategories() {
                Modules.registerCategory(Main);
        }

        @Override
        public String getPackage() {
                return "com.example.addon";
        }
}
