package org.cobbleutils.cobblecomputils.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.class_310;
import org.cobbleutils.cobblecomputils.capture.GymNet;
import org.cobbleutils.cobblecomputils.capture.ListNet;
import org.cobbleutils.cobblecomputils.capture.MartNet;
import org.cobbleutils.cobblecomputils.capture.TradeNet;
import org.cobbleutils.cobblecomputils.evedit.EvNet;
import org.cobbleutils.cobblecomputils.movetutor.TutorNet;

@Environment(EnvType.CLIENT)
public class CobblecomputilsClient implements ClientModInitializer {
   public void onInitializeClient() {
      SkinChestScreen.register();
      ClientPlayNetworking.registerGlobalReceiver(TutorNet.Open.ID, (payload, context) -> {
         class_310 client = context.client();
         if (client.field_1755 instanceof TutorScreen screen) {
            screen.update(payload);
         } else {
            client.method_1507(new TutorScreen(payload));
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(EvNet.Open.ID, (payload, context) -> {
         class_310 client = context.client();
         if (client.field_1755 instanceof EvScreen screen) {
            screen.update(payload);
         } else {
            client.method_1507(new EvScreen(payload));
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(GymNet.View.ID, (payload, context) -> {
         class_310 client = context.client();
         if (payload.stacks().isEmpty()) {
            if (client.field_1755 instanceof GymScreen screen) screen.closeFromServer();
         } else if (client.field_1755 instanceof GymScreen screen) {
            screen.update(payload);
         } else {
            client.method_1507(new GymScreen(payload));
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(MartNet.Open.ID, (payload, context) -> {
         class_310 client = context.client();
         if (client.field_1755 instanceof MartScreen screen) {
            screen.update(payload);
         } else {
            client.method_1507(new MartScreen(payload));
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(MartNet.Result.ID, (payload, context) -> {
         if (context.client().field_1755 instanceof MartScreen screen) screen.result(payload);
      });
      ClientPlayNetworking.registerGlobalReceiver(ListNet.View.ID, (payload, context) -> {
         class_310 client = context.client();
         Object cur = client.field_1755;
         if (payload.rows().isEmpty() && payload.title().isEmpty()) {
            if (cur instanceof ListScreen screen) screen.closeFromServer();
            else if (cur instanceof HubScreen hub) hub.closeFromServer();
         } else if (payload.tiles()) {
            if (cur instanceof HubScreen hub) {
               hub.update(payload);
            } else {
               if (cur instanceof ListScreen ls) ls.markReplaced();
               client.method_1507(new HubScreen(payload));
            }
         } else if (cur instanceof ListScreen screen) {
            screen.update(payload);
         } else {
            if (cur instanceof HubScreen hub) hub.markReplaced();
            client.method_1507(new ListScreen(payload));
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(TradeNet.View.ID, (payload, context) -> {
         class_310 client = context.client();
         if (payload.partner().isEmpty()) {
            if (client.field_1755 instanceof TradeScreen screen) screen.closeFromServer();
         } else if (client.field_1755 instanceof TradeScreen screen) {
            screen.update(payload);
         } else {
            client.method_1507(new TradeScreen(payload));
         }
      });
   }
}
