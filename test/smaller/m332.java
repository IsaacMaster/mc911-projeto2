// testando new object - STILL NOT WORKING
class m332
{
   public static void main(String[] args)
   {
      System.out.println(new a().i());
   }
}

class a
{
   a a;
   public a A(){a = new a(); return this;}
   public int i(){ return 0; }
}