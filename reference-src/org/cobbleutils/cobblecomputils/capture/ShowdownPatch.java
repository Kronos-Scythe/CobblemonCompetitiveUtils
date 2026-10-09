package org.cobbleutils.cobblecomputils.capture;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.ModInitializer;

public final class ShowdownPatch implements ModInitializer {
   public void onInitialize() {
      apply();

      try {
         Class var1 = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents");
         Object var2 = var1.getField("SERVER_STARTING").get(null);
         Class var3 = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStarting");
         Object var4 = Proxy.newProxyInstance(ShowdownPatch.class.getClassLoader(), new Class[]{var3}, (var0, var1x, var2x) -> {
            if (var1x.getName().equals("onServerStarting")) {
               apply();
            }

            return null;
         });
         Class.forName("net.fabricmc.fabric.api.event.Event").getMethod("register", Object.class).invoke(var2, var4);
         hook("SERVER_STARTED", "ServerStarted", "onServerStarted");
         hook("END_DATA_PACK_RELOAD", "EndDataPackReload", "endDataPackReload");
      } catch (Throwable var5) {
         System.out.println("[cobblecomputils] Dark Void patch: could not hook server start: " + var5);
      }
   }

   private static final String DV = "({num:464,accuracy:50,basePower:0,category:\"Status\",name:\"Dark Void\",pp:10,priority:0,critRatio:1,flags:{protect:1,reflectable:1,mirror:1,metronome:1},status:\"slp\",noSketch:false,secondary:null,target:\"allAdjacentFoes\",type:\"Dark\",zMove:{effect:\"clearnegativeboost\"},contestType:\"Clever\"})";

   private static void hook(String field, String iface, String method) throws Exception {
      Class<?> ev = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents");
      Object event = ev.getField(field).get(null);
      Class<?> cb = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$" + iface);
      Object proxy = Proxy.newProxyInstance(ShowdownPatch.class.getClassLoader(), new Class[]{cb}, (p, m, a) -> {
         if (m.getName().equals(method)) {
            scheduleLive();
         }
         return null;
      });
      Class.forName("net.fabricmc.fabric.api.event.Event").getMethod("register", Object.class).invoke(event, proxy);
   }

   private static void scheduleLive() {
      Thread t = new Thread(() -> {
         for (long ms : new long[]{3000L, 15000L, 45000L}) {
            try {
               Thread.sleep(ms);
            } catch (InterruptedException e) {
               return;
            }
            live();
         }
      }, "cobblecomputils-darkvoid");
      t.setDaemon(true);
      t.start();
   }

   /** Replace Dark Void inside the already-running battle engine (no restart needed). */
   public static void live() {
      try {
         Object thread = com.cobblemon.mod.common.Cobblemon.INSTANCE.getShowdownThread();
         kotlin.jvm.functions.Function1<Object, kotlin.Unit> task = service -> {
            try {
               Object ctx = service.getClass().getMethod("getContext").invoke(service);
               Object bindings = ctx.getClass().getMethod("getBindings", String.class).invoke(ctx, "js");
               java.lang.reflect.Method getMember = bindings.getClass().getMethod("getMember", String.class);
               Object recv = getMember.invoke(bindings, "receiveMoveData");
               java.lang.reflect.Method exec = recv.getClass().getMethod("execute", Object[].class);
               exec.invoke(recv, (Object) new Object[]{"darkvoid", DV});
               Object after = getMember.invoke(bindings, "afterSpeciesInit");
               exec.invoke(after, (Object) new Object[0]);
            } catch (Throwable e) {
               System.out.println("[cobblecomputils] Dark Void live patch failed: " + e);
            }
            return kotlin.Unit.INSTANCE;
         };
         for (java.lang.reflect.Method m : thread.getClass().getMethods()) {
            if (m.getName().equals("queue") && m.getParameterCount() == 1) {
               m.invoke(thread, task);
               return;
            }
         }
         System.out.println("[cobblecomputils] Dark Void live patch: no queue method");
      } catch (Throwable e) {
         System.out.println("[cobblecomputils] Dark Void live patch failed: " + e);
      }
   }

   public static boolean apply() {
      return apply(new File("showdown/data/moves.js"));
   }

   public static boolean apply(File var0) {
      try {
         if (!var0.isFile()) {
            return false;
         } else {
            String var1 = new String(Files.readAllBytes(var0.toPath()), StandardCharsets.UTF_8);
            int var2 = var1.indexOf("darkvoid: {");
            if (var2 < 0) {
               return false;
            } else {
               int var3 = var1.indexOf("\n  },", var2);
               String var4 = var1.substring(var2, var3 < 0 ? var1.length() : var3);
               Matcher var5 = Pattern.compile(
                     "\\n\\s*onTry\\(source, target, move\\) \\{\\s*if \\(source\\.species\\.name === \"Darkrai\" \\|\\| move\\.hasBounced\\) \\{\\s*return;\\s*\\}\\s*this\\.add\\(\"-fail\", source, \"move: Dark Void\"\\);\\s*this\\.hint\\(\"Only a Pokemon whose form is Darkrai can use this move\\.\"\\);\\s*return null;\\s*\\},"
                  )
                  .matcher(var4);
               if (!var5.find()) {
                  return false;
               } else {
                  String var6 = var1.substring(0, var2) + var4.substring(0, var5.start()) + var4.substring(var5.end()) + (var3 < 0 ? "" : var1.substring(var3));
                  File var7 = new File(var0.getPath() + ".bak-cobblecomputils");
                  if (!var7.exists()) {
                     Files.copy(var0.toPath(), var7.toPath(), StandardCopyOption.REPLACE_EXISTING);
                  }

                  Files.write(var0.toPath(), var6.getBytes(StandardCharsets.UTF_8));
                  System.out
                     .println(
                        "[cobblecomputils] Dark Void is no longer Darkrai-only (patched "
                           + var0.getPath()
                           + "). If a battle server is already running, restart once."
                     );
                  return true;
               }
            }
         }
      } catch (Throwable var8) {
         System.out.println("[cobblecomputils] Dark Void patch failed: " + var8);
         return false;
      }
   }

   public static void main(String[] var0) {
      System.out.println(apply(new File(var0[0])));
   }
}
