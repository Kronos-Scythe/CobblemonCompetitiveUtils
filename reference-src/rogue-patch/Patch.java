import java.io.*; import java.nio.file.*; import java.util.*; import java.util.zip.*;
import org.objectweb.asm.*; import org.objectweb.asm.tree.*;
public class Patch {
  public static void main(String[] a) throws Exception {
    String in=a[0], out=a[1], gate=a[2];
    try (ZipFile zf=new ZipFile(in); ZipOutputStream zo=new ZipOutputStream(new FileOutputStream(out))) {
      for (Enumeration<? extends ZipEntry> e=zf.entries(); e.hasMoreElements();) {
        ZipEntry ze=e.nextElement(); byte[] b=zf.getInputStream(ze).readAllBytes();
        String n=ze.getName();
        if (n.equals("raidqueue/RaidDenQueueManager.class")) b=patch(b,"join","(Lnet/minecraft/class_3222;I)V",1);
        if (n.equals("raidqueue/gui/QueueLobbyMenu.class")) b=patch(b,"open","(Lnet/minecraft/class_3222;I)V",1);
        if (n.equals("raidqueue/raiddens/RaidDenLauncher.class")) b=patch(b,"tryLaunch",null,2);
        if (n.equals("fabric.mod.json")) b=new String(b).replaceAll("\"version\"\\s*:\\s*\"1.0.0\"","\"version\": \"1.0.0-pp1\"").getBytes();
        zo.putNextEntry(new ZipEntry(n)); zo.write(b); zo.closeEntry();
      }
      zo.putNextEntry(new ZipEntry("raidqueue/PassGate.class")); zo.write(Files.readAllBytes(Paths.get(gate))); zo.closeEntry();
    } catch (ZipException x) { }
  }
  static byte[] patch(byte[] b,String name,String desc,int mode){
    ClassNode cn=new ClassNode(); new ClassReader(b).accept(cn,0);
    int done=0;
    for (MethodNode m: cn.methods) {
      if (!m.name.equals(name)) continue; if (desc!=null && !m.desc.equals(desc)) continue;
      Type[] ts=Type.getArgumentTypes(m.desc);
      int pi=-1, ti=-1, li=-1, idx=m.access>>3&1; // static assumed
      if ((m.access & Opcodes.ACC_STATIC)==0) idx=1; else idx=0;
      int[] slot=new int[ts.length]; int s=idx; for(int i=0;i<ts.length;i++){slot[i]=s; s+=ts[i].getSize();}
      InsnList il=new InsnList(); LabelNode ok=new LabelNode();
      if (mode==1) { il.add(new VarInsnNode(Opcodes.ALOAD,slot[0])); il.add(new VarInsnNode(Opcodes.ILOAD,slot[1])); il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"raidqueue/PassGate","blocked","(Lnet/minecraft/class_3222;I)Z",false)); }
      else { // tryLaunch(world, List, int)
        int li2=-1; for(int i=0;i<ts.length;i++) if(ts[i].getClassName().equals("java.util.List")) li2=i;
        il.add(new VarInsnNode(Opcodes.ALOAD,slot[li2])); il.add(new VarInsnNode(Opcodes.ILOAD,slot[ts.length-1])); il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"raidqueue/PassGate","blockedAll","(Ljava/util/List;I)Z",false)); }
      il.add(new JumpInsnNode(Opcodes.IFEQ,ok));
      Type rt=Type.getReturnType(m.desc);
      if (rt.getSort()==Type.VOID) il.add(new InsnNode(Opcodes.RETURN)); else { il.add(new InsnNode(Opcodes.ACONST_NULL)); il.add(new InsnNode(Opcodes.ARETURN)); }
      il.add(ok);
      m.instructions.insert(il); done++;
      System.out.println("patched "+cn.name+"."+m.name+m.desc+" slots="+Arrays.toString(slot));
    }
    if (done==0) throw new RuntimeException("no method "+name+" in "+cn.name);
    ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_FRAMES){ protected String getCommonSuperClass(String x,String y){return "java/lang/Object";}};
    cn.accept(cw); return cw.toByteArray();
  }
}
