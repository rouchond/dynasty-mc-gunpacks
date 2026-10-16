import java.nio.file.*;
import java.util.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;

/** Add supply tooltips to the main profile's own screen; preserve every other method. */
public final class MainSupplyScreenPatch implements Opcodes {
    static String text(MethodNode method) {
        Textifier t=new Textifier();method.accept(new TraceMethodVisitor(t));StringWriter out=new StringWriter();t.print(new PrintWriter(out));return out.toString();
    }
    public static void main(String[] args) throws Exception {
        byte[] original=Files.readAllBytes(Path.of(args[0]));ClassNode node=new ClassNode();new ClassReader(original).accept(node,0);
        Map<String,String> before=new HashMap<>();for(var m:node.methods)before.put(m.name+m.desc,text(m));
        String helper="dynasty$supplyInventoryLabel",stack="Lnet/minecraft/world/item/ItemStack;";
        if(node.methods.stream().anyMatch(m->m.name.equals(helper)))throw new IllegalStateException("Already patched");
        Map<String,String> labels=Map.of(
            "Examples: wheat (1 point), bread (2), cooked meat (3)","Crops, ingredients, cooked foods and whole meals; hover inventory for values.",
            "Examples: cobblestone or planks (1 point), logs (4)","Stone, timber, glass, concrete, copper and industrial building materials.",
            "Examples: lanterns (2 points), andesite alloy (1)","Metals, lighting, books, storage and Create/TFMG machine parts.",
            "Examples: leather (1 point), undamaged shields (6)","Leather, ammunition materials, metal plates and undamaged weapons/armor.");
        int replaced=0,inserted=0;
        for(var m:node.methods)if(m.name.equals("reserves")&&m.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;)V")) {
            for(var n:m.instructions.toArray()) {
                if(n instanceof LdcInsnNode ldc && ldc.cst instanceof String s && labels.containsKey(s)){ldc.cst=labels.get(s);replaced++;}
                if(n instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/item/ItemStack") && call.name.equals("getHoverName")) {
                    AbstractInsnNode next=n.getNext();while(next!=null&&next.getOpcode()<0){if(next instanceof FrameNode)throw new IllegalStateException("Unexpected stack frame");next=next.getNext();}
                    if(!(next instanceof MethodInsnNode getter)||!getter.name.equals("getString")||!getter.desc.equals("()Ljava/lang/String;"))throw new IllegalStateException("Unexpected hover code");
                    m.instructions.insertBefore(n,new InsnNode(DUP));
                    m.instructions.insert(next,new MethodInsnNode(INVOKESTATIC,node.name,helper,"("+stack+"Ljava/lang/String;)Ljava/lang/String;",false));inserted++;
                }
            }
        }
        if(replaced!=4||inserted!=1)throw new IllegalStateException("Unexpected main screen: "+replaced+" labels, "+inserted+" hover");
        MethodNode m=new MethodNode(ACC_PRIVATE|ACC_STATIC,helper,"("+stack+"Ljava/lang/String;)Ljava/lang/String;",null,null);
        m.instructions.add(new VarInsnNode(ALOAD,1));m.instructions.add(new LdcInsnNode(" | "));
        m.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","concat","(Ljava/lang/String;)Ljava/lang/String;",false));
        m.instructions.add(new VarInsnNode(ALOAD,0));
        m.instructions.add(new MethodInsnNode(INVOKESTATIC,"com/dynastymc/settlements/ReserveValues","describe","("+stack+")Ljava/lang/String;",false));
        m.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","concat","(Ljava/lang/String;)Ljava/lang/String;",false));m.instructions.add(new InsnNode(ARETURN));node.methods.add(m);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(new CheckClassAdapter(writer,false));byte[] patched=writer.toByteArray();
        ClassNode after=new ClassNode();new ClassReader(patched).accept(after,0);int preserved=0;
        for(var method:after.methods)if(!method.name.equals("reserves")&&!method.name.equals(helper)){
            if(!Objects.equals(before.get(method.name+method.desc),text(method)))throw new IllegalStateException("Unrelated method changed: "+method.name);preserved++;
        }
        Files.write(Path.of(args[1]),patched);System.out.println("Main screen: four labels and one supply hover patched; "+preserved+" other methods preserved; ASM structure validated.");
    }
}
