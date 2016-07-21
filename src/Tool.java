import static org.objectweb.asm.Opcodes.ASM5;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

public class Tool {
  
  enum Values implements Value {
    NONE, SITES;
    
    @Override
    public int getSize() {
      return 1;
    }
  }
  
  static class MethodName implements Value {
    final String name;
    
    MethodName(String name) {
      this.name = name;
    }
    
    @Override
    public int getSize() {
      return 1;
    }
  }
  
  static class AbstractExecutionInterpreter extends Interpreter<Value> {
    private final BasicInterpreter interpreter = new BasicInterpreter();
    Consumer<InsnList> pending = __ -> { /* empty */ };
    Consumer<InsnList> current;

    private static final Handle BSM = new Handle(H_INVOKESTATIC, "RT", "bsm",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;",
        false);
    
    public AbstractExecutionInterpreter() {
      super(ASM5);
    }

    private static BasicValue asBasicValue(AbstractInsnNode insn, Value value) {
      if (!(value instanceof BasicValue)) {
        throw new IllegalStateException("invalid pattern " + insn);
      }
      return (BasicValue)value;
    }
    
    @Override
    public Value newValue(Type type) {
      return interpreter.newValue(type);
    }

    @Override
    public Value newOperation(AbstractInsnNode insn) throws AnalyzerException {
      return interpreter.newOperation(insn);
    }

    @Override
    public Value copyOperation(AbstractInsnNode insn, Value value) throws AnalyzerException {
      return interpreter.copyOperation(insn, asBasicValue(insn, value));
    }

    @Override
    public Value unaryOperation(AbstractInsnNode insn, Value value) throws AnalyzerException {
      if (insn.getOpcode() == GETFIELD && value == Values.SITES) {
        // remove the getfield instruction
        current = current.andThen(insns -> insns.remove(insn));
        return new MethodName(((FieldInsnNode)insn).name);
      }
      return interpreter.unaryOperation(insn, asBasicValue(insn, value));
    }

    @Override
    public Value binaryOperation(AbstractInsnNode insn, Value value1, Value value2) throws AnalyzerException {
      return interpreter.binaryOperation(insn, asBasicValue(insn, value1), asBasicValue(insn, value2));
    }

    @Override
    public Value ternaryOperation(AbstractInsnNode insn, Value value1, Value value2, Value value3) throws AnalyzerException {
      return interpreter.ternaryOperation(insn, asBasicValue(insn, value1), asBasicValue(insn, value2), asBasicValue(insn, value3));
    }

    @Override
    public Value naryOperation(AbstractInsnNode insn, List<? extends Value> values) throws AnalyzerException {
      if (insn.getOpcode() == INVOKESTATIC) {
        MethodInsnNode methodInsn = (MethodInsnNode) insn;
        if (methodInsn.owner.equals("Test") &&
            methodInsn.name.equals("sites") &&
            methodInsn.desc.equals("(LContext;)LTest$Sites;")) {
          // pending: remove the static call and remove the context on top of the stack
          current = insns -> insns.set(insn, new InsnNode(Opcodes.POP)); 
          return Values.SITES;
        }
      }
      else if (insn.getOpcode() == INVOKEINTERFACE) {
        MethodInsnNode methodInsn = (MethodInsnNode) insn;
        Value receiver = null;
        if (methodInsn.owner.equals("CachedMethod") &&
            methodInsn.name.equals("call") &&
            values.size() >= 1 &&
            (receiver = values.get(0)) instanceof MethodName) {
          
          // we have found the method "call"
          String name = ((MethodName)receiver).name;
          String desc = methodInsn.desc;
          // pending: replace by invokedynamic
          current = current.andThen(insns -> insns.set(insn, new InvokeDynamicInsnNode(name, desc, BSM, "hello")));
          pending = pending.andThen(current);
          current = null;
          return BasicValue.REFERENCE_VALUE;
        }
      }
      return interpreter.naryOperation(insn, values.stream().map(v -> asBasicValue(insn, v)).collect(Collectors.toList()));
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, Value value, Value expected) {
      // empty
    }

    @Override
    public Value merge(Value v, Value w) {
      return Values.NONE;
    }
  }
  
  public static void main(String[] args) throws IOException {
    Path path = Paths.get(args[0]);
    ClassReader reader = new ClassReader(Files.readAllBytes(path));
    ClassWriter writer = new ClassWriter(reader, 0);
    
    reader.accept(new ClassVisitor(ASM5, writer) {
      String owner;
      
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        owner = name;
      }
      
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodNode node = new MethodNode(access, name, desc, signature, exceptions);
        return new MethodVisitor(ASM5, node) {
          @Override
          public void visitEnd() {
            super.visitEnd();
            
            AbstractExecutionInterpreter interpreter = new AbstractExecutionInterpreter();
            Analyzer<Value> a = new Analyzer<>(interpreter);
            try {
              a.analyze(owner, node);
            } catch(AnalyzerException e) {
              throw new AssertionError(e);
            }
            //Frame<Value>[] frames = a.getFrames();
            
            // reshape the bytecode by executing the pending code
            interpreter.pending.accept(node.instructions);
            
            // write the transformed method
            node.accept(writer);
          }
        };
      }
    }, 0);
    
    Path output = Paths.get(args[1]);
    Files.write(output, writer.toByteArray());
  }
}
