/*****************************************************
Esta classe Codegen é a responsável por emitir LLVM-IR. 
Ela possui o mesmo método 'visit' sobrecarregado de
acordo com o tipo do parâmetro. Se o parâmentro for
do tipo 'While', o 'visit' emitirá código LLVM-IR que 
representa este comportamento. 
Alguns métodos 'visit' já estão prontos e, por isso,
a compilação do código abaixo já é possível.

class a{
    public static void main(String[] args){
    	System.out.println(1+2);
    }
}

O pacote 'llvmast' possui estruturas simples 
que auxiliam a geração de código em LLVM-IR. Quase todas 
as classes estão prontas; apenas as seguintes precisam ser 
implementadas: 

// llvmasm/LlvmBranch.java OK
// llvmasm/LlvmIcmp.java OK
// llvmasm/LlvmMinus.java OK
// llvmasm/LlvmTimes.java OK


Todas as assinaturas de métodos e construtores 
necessárias já estão lá. 


Observem todos os métodos e classes já implementados
e o manual do LLVM-IR (http://llvm.org/docs/LangRef.html) 
como guia no desenvolvimento deste projeto. 

****************************************************/
package llvm;

import semant.Env;
import syntaxtree.*;
import llvmast.*;

import java.util.*;

public class Codegen extends VisitorAdapter{
	private List<LlvmInstruction> assembler;
	private Codegen codeGenerator;

  	private SymTab symTab;
	private ClassNode classEnv; 	// Aponta para a classe atualmente em uso em symTab
	private MethodNode methodEnv; 	// Aponta para a metodo atualmente em uso em symTab
	
	private int ifCount;
	private int whileCount;


	public Codegen(){
		assembler = new LinkedList<LlvmInstruction>();
		ifCount = 0;
		whileCount = 0;
	}

	// Método de entrada do Codegen
	public String translate(Program p, Env env){	
		codeGenerator = new Codegen();
		
		// Preenchendo a Tabela de Símbolos
		// Quem quiser usar 'env', apenas comente essa linha
		// codeGenerator.symTab.FillTabSymbol(p);
		
		// Formato da String para o System.out.printlnijava "%d\n"
		codeGenerator.assembler.add(new LlvmConstantDeclaration("@.formatting.string", "private constant [4 x i8] c\"%d\\0A\\00\""));	

		// NOTA: sempre que X.accept(Y), então Y.visit(X);
		// NOTA: Logo, o comando abaixo irá chamar codeGenerator.visit(Program), linha 75
		p.accept(codeGenerator);

		// Link do printf
		List<LlvmType> pts = new LinkedList<LlvmType>();
		pts.add(new LlvmPointer(LlvmPrimitiveType.I8));
		pts.add(LlvmPrimitiveType.DOTDOTDOT);
		codeGenerator.assembler.add(new LlvmExternalDeclaration("@printf", LlvmPrimitiveType.I32, pts)); 
		List<LlvmType> mallocpts = new LinkedList<LlvmType>();
		mallocpts.add(LlvmPrimitiveType.I32);
		codeGenerator.assembler.add(new LlvmExternalDeclaration("@malloc", new LlvmPointer(LlvmPrimitiveType.I8),mallocpts)); 


		String r = new String();
		for(LlvmInstruction instr : codeGenerator.assembler)
			r += instr+"\n";
		return r;
	}

	public LlvmValue visit(Program n){
		n.mainClass.accept(this);

		for (util.List<ClassDecl> c = n.classList; c != null; c = c.tail)
			c.head.accept(this);

		return null;
	}

	public LlvmValue visit(MainClass n){
		
		// definicao do main 
		assembler.add(new LlvmDefine("@main", LlvmPrimitiveType.I32, new LinkedList<LlvmValue>()));
		assembler.add(new LlvmLabel(new LlvmLabelValue("entry")));
		LlvmRegister R1 = new LlvmRegister(new LlvmPointer(LlvmPrimitiveType.I32));
		assembler.add(new LlvmAlloca(R1, LlvmPrimitiveType.I32, new LinkedList<LlvmValue>()));
		assembler.add(new LlvmStore(new LlvmIntegerLiteral(0), R1));

		// Statement é uma classe abstrata
		// Portanto, o accept chamado é da classe que implementa Statement, por exemplo,  a classe "Print". 
		n.stm.accept(this);  

		// Final do Main
		LlvmRegister R2 = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmLoad(R2,R1));
		assembler.add(new LlvmRet(R2));
		assembler.add(new LlvmCloseDefinition());
		return null;
	}
	
	public LlvmValue visit(Plus n){
		
		LlvmValue v1 = n.lhs.accept(this);
		LlvmValue v2 = n.rhs.accept(this);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmPlus(lhs,LlvmPrimitiveType.I32,v1,v2));
		return lhs;
	}
	
	public LlvmValue visit(Print n){

		LlvmValue v =  n.exp.accept(this);

		// getelementptr:
		LlvmRegister lhs = new LlvmRegister(new LlvmPointer(LlvmPrimitiveType.I8));
		LlvmRegister src = new LlvmNamedValue("@.formatting.string",new LlvmPointer(new LlvmArray(4,LlvmPrimitiveType.I8)));
		List<LlvmValue> offsets = new LinkedList<LlvmValue>();
		offsets.add(new LlvmIntegerLiteral(0));
		offsets.add(new LlvmIntegerLiteral(0));
		List<LlvmType> pts = new LinkedList<LlvmType>();
		pts.add(new LlvmPointer(LlvmPrimitiveType.I8));
		List<LlvmValue> args = new LinkedList<LlvmValue>();
		args.add(lhs);
		args.add(v);
		assembler.add(new LlvmGetElementPointer(lhs,src,offsets));

		pts = new LinkedList<LlvmType>();
		pts.add(new LlvmPointer(LlvmPrimitiveType.I8));
		pts.add(LlvmPrimitiveType.DOTDOTDOT);
		
		// printf:
		assembler.add(new LlvmCall(new LlvmRegister(LlvmPrimitiveType.I32),
				LlvmPrimitiveType.I32,
				pts,				 
				"@printf",
				args
				));
		return null;
	}
	
	public LlvmValue visit(IntegerLiteral n){
		return new LlvmIntegerLiteral(n.value);
	}
	
	// Todos os visit's que devem ser implementados	
	public LlvmValue visit(ClassDeclSimple n){
		
		int a, b, i, j;
		List<LlvmType> locals = new ArrayList<LlvmType>();
		
		b = n.varList.size();
		for (a = 0; a < b; a++) {
			// TODO fill locals list
		}
		
		
		b = n.methodList.size();
		for (a = 0; a < b; a++) {
			MethodDecl method = n.methodList.head;
			assembler.add(new LlvmDefine(null, null, null)); // TODO fill w/ correct parameters
			j = method.body.size();
			for (i = 0; i < j; i++) {
				method.body.head.accept(this);
				method.body = method.body.tail;
			}
			assembler.add(new LlvmCloseDefinition());		
			method.body = method.body.tail;
		}
		return null;
	}
	
	public LlvmValue visit(ClassDeclExtends n){
		return null;
	}
	
	public LlvmValue visit(VarDecl n){
		return null;
	}
	
	public LlvmValue visit(MethodDecl n){
		return null;
	}
	
	public LlvmValue visit(Formal n){
		return null;
	}
	
	public LlvmValue visit(IntArrayType n){
		return null;
	}
	
	public LlvmValue visit(BooleanType n){
		return null;
	}
	
	public LlvmValue visit(IntegerType n){
		return null;
	}
	
	public LlvmValue visit(IdentifierType n){
		return null;
	}
	
	public LlvmValue visit(Block n){
		
		int i, j;
		j = n.body.size();
		for(i = 0; i < j; i++){
			n.body.head.accept(this);
			n.body = n.body.tail;
		}
		
		return null;
		
	}
	
	public LlvmValue visit(If n){
				
		LlvmLabelValue ifThenLabel = new LlvmLabelValue("ifThen"+ifCount);
		LlvmLabelValue ifElseLabel = new LlvmLabelValue("ifElse"+ifCount);
		LlvmLabelValue ifEndLabel = new LlvmLabelValue("ifEnd"+ifCount);
		ifCount++;
		
		LlvmValue cond = n.condition.accept(this);
		assembler.add(new LlvmBranch(cond,
					ifThenLabel,
					ifElseLabel)
				);
		
		
		LlvmLabel ifThen = new LlvmLabel(ifThenLabel);
		assembler.add(ifThen);
		n.thenClause.accept(this);
		assembler.add(new LlvmBranch(ifEndLabel));
		
		LlvmLabel ifElse = new LlvmLabel(ifElseLabel);
		assembler.add(ifElse);
		n.elseClause.accept(this);
		assembler.add(new LlvmBranch(ifEndLabel));
		
		LlvmLabel ifEnd = new LlvmLabel(ifEndLabel);
		assembler.add(ifEnd);
		return null;
		
	}
	
	public LlvmValue visit(While n){
		
		LlvmLabelValue whileCondLabel = new LlvmLabelValue("whileCond"+whileCount);
		LlvmLabelValue whileBodyLabel = new LlvmLabelValue("whileBody"+whileCount);
		LlvmLabelValue whileEndLabel = new LlvmLabelValue("whileEnd"+whileCount);
		whileCount++;
		
		LlvmLabel whileCond = new LlvmLabel(whileCondLabel);
		assembler.add(whileCond);
		
		LlvmValue cond = n.condition.accept(this);
		assembler.add(new LlvmBranch(cond,
					whileBodyLabel,
					whileEndLabel)
				);
		
		
		LlvmLabel whileBody = new LlvmLabel(whileBodyLabel);
		assembler.add(whileBody);
		n.body.accept(this);
		assembler.add(new LlvmBranch(whileCondLabel));
		
		LlvmLabel whileEnd = new LlvmLabel(whileEndLabel);
		assembler.add(whileEnd);
		return null;
		
	}
	
	public LlvmValue visit(Assign n){
		return null;
	}
	
	public LlvmValue visit(ArrayAssign n){
		return null;
	}
	
	public LlvmValue visit(And n){
		
		LlvmValue lhs = n.lhs.accept(this);
		LlvmValue rhs = n.rhs.accept(this);
		LlvmRegister mul = new LlvmRegister(lhs.type);
		assembler.add(new LlvmTimes(mul, lhs.type, lhs, rhs));
		LlvmRegister res = new LlvmRegister(LlvmPrimitiveType.I1);
		assembler.add(new LlvmIcmp(res, 2, mul.type, mul, new LlvmIntegerLiteral(0)));
		return res;
		
	}
	
	public LlvmValue visit(LessThan n){
		
		LlvmValue v1 = n.lhs.accept(this);
		LlvmValue v2 = n.rhs.accept(this);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmIcmp(lhs,9,LlvmPrimitiveType.I32,v1,v2));
		return lhs;
		
	}
	
	public LlvmValue visit(Equal n){
		
		LlvmValue v1 = n.lhs.accept(this);
		LlvmValue v2 = n.rhs.accept(this);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmIcmp(lhs,1,LlvmPrimitiveType.I32,v1,v2));
		return lhs;
		
	}
	
	public LlvmValue visit(Minus n){
		
		LlvmValue v1 = n.lhs.accept(this);
		LlvmValue v2 = n.rhs.accept(this);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmMinus(lhs,LlvmPrimitiveType.I32,v1,v2));
		return lhs;
		
	}
	
	public LlvmValue visit(Times n){
		
		LlvmValue v1 = n.lhs.accept(this);
		LlvmValue v2 = n.rhs.accept(this);
		LlvmRegister lhs = new LlvmRegister(LlvmPrimitiveType.I32);
		assembler.add(new LlvmTimes(lhs,LlvmPrimitiveType.I32,v1,v2));
		return lhs;
		
	}
	
	public LlvmValue visit(ArrayLookup n){
		return null;
	}
	
	public LlvmValue visit(ArrayLength n){
		return null;
	}
	
	public LlvmValue visit(Call n){
		
		/*assembler.add(new LlvmCall(
				n.object,
				n.method,
				n.actuals));
		return null;
		
		for reference: printf call
		assembler.add(new LlvmCall(
				new LlvmRegister(LlvmPrimitiveType.I32),
				LlvmPrimitiveType.I32,
				pts,				 
				"@printf",
				args
				));*/
		return null;
		
	}
	
	public LlvmValue visit(True n){
		
		return new LlvmBool(1);
		
	}
	
	public LlvmValue visit(False n){
		
		return new LlvmBool(0);
		
	}
	
	public LlvmValue visit(IdentifierExp n){
		return null;
	}
	
	public LlvmValue visit(This n){
		
		return n.accept(this);
		// TODO check if this is really enough
	}
	
	
	public LlvmValue visit(NewArray n){
		return null;
	}
	
	public LlvmValue visit(NewObject n){
		return null;
	}
	
	public LlvmValue visit(Not n){
		
		LlvmValue exp = n.exp.accept(this);
		// TODO return bool value opposite to exp
		return null;
		
	}
	
	public LlvmValue visit(Identifier n){
		return null;
	}
}


/**********************************************************************************/
/* === Tabela de Símbolos ==== 
 * 
 * 
 */
/**********************************************************************************/

class SymTab extends VisitorAdapter{
    public Map<String, ClassNode> classes;     
    private ClassNode classEnv;    //aponta para a classe em uso

    public LlvmValue FillTabSymbol(Program n){
	n.accept(this);
	return null;
}
public LlvmValue visit(Program n){
	n.mainClass.accept(this);

	for (util.List<ClassDecl> c = n.classList; c != null; c = c.tail)
		c.head.accept(this);

	return null;
}

public LlvmValue visit(MainClass n){
	classes.put(n.className.s, new ClassNode(n.className.s, null, null));
	return null;
}

public LlvmValue visit(ClassDeclSimple n){
	List<LlvmType> typeList = null;
	// Constroi TypeList com os tipos das variáveis da Classe (vai formar a Struct da classe)
	
	List<LlvmValue> varList = null;
	// Constroi VarList com as Variáveis da Classe

	classes.put(n.name.s, new ClassNode(n.name.s, 
										new LlvmStructure(typeList), 
										varList)
      			);
    	// Percorre n.methodList visitando cada método
	return null;
}

	public LlvmValue visit(ClassDeclExtends n){
		return null;
	}
	
	public LlvmValue visit(VarDecl n){
		return null;
	}
	
	public LlvmValue visit(Formal n){
		return null;
	}
	
	public LlvmValue visit(MethodDecl n){
		return null;
	}
	
	public LlvmValue visit(IdentifierType n){
		return null;
	}
	
	public LlvmValue visit(IntArrayType n){
		return null;
	}
	
	public LlvmValue visit(BooleanType n){
		return null;
	}
	
	public LlvmValue visit(IntegerType n){
		return null;
	}
}

class ClassNode extends LlvmType {
	ClassNode (String nameClass, LlvmStructure classType, List<LlvmValue> varList){
	}
}

class MethodNode {
}




