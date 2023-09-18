package jenkins.mvn.test.bundle.print.impl;

import jenkins.mvn.test.bundle.print.PrintService;

public class PrintServiceImpl implements PrintService
{
    public void print( String msg )
    {
        System.out.println( msg );
    }

    public static void main( String[] args )
    {
        PrintService printer = new PrintServiceImpl();
        printer.print( "Hello World" );
    }
}
