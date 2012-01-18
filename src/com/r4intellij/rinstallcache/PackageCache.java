/*
 * Copyright 2011 Holger Brandl
 *
 * This code is licensed under BSD. For details see
 * http://www.opensource.org/licenses/bsd-license.php
 */

package com.r4intellij.rinstallcache;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * DOCUMENT ME!
 *
 * @author Holger Brandl
 */
public class PackageCache extends HashSet<RPackage> {

    public static void main(String[] args) throws IOException, InterruptedException {
//        System.err.println(getListOfInstalledPackages());
//        RPackage plyrFuns = buildPackageCache("stats");

//        System.err.println("plyrFuns");
//        HashSet<RPackage> libraryCache = getLibraryCache();
//        System.err.println("cached " + libraryCache.size() + " packages!");

        PackageCache libraryCache = getLibraryCache();
        System.err.println(libraryCache.getPackagesOfFunction("a_ply"));
    }


    private static PackageCache packageCache;

    private PackageCache() {

    }

    public static PackageCache getLibraryCache() {
        if (packageCache == null) {
            packageCache = new PackageCache();

            try {
                List<String> installedPackages = getListOfInstalledPackages();
                for (String packageName : installedPackages) {
                    packageCache.add(buildPackageCache(packageName));
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        return packageCache;
    }


    private static List<String> getListOfInstalledPackages() throws IOException, InterruptedException {
        String output = CachingUtils.evalRComand("paste(search(), collapse=', ')");
//        System.err.println("output was " + output.getOutput());

        Pattern pattern = Pattern.compile("package:([^,]*),");
        Matcher matcher = pattern.matcher(output);

        List<String> installedPackages = new ArrayList<String>();
        while (matcher.find()) {
//                System.err.println("pacakge is "+ matcher.group(1));
            installedPackages.add(matcher.group(1));
        }

        return installedPackages;
    }


    private static RPackage buildPackageCache(String packageName) throws IOException, InterruptedException {
        System.err.println("rebuilding cache of " + packageName);
        String lineBreaker = "&&&&";
        String output = CachingUtils.evalRComand("pckgDocu <-library(help = " + packageName + "); paste(pckgDocu$info[[2]], collapse=\"" + lineBreaker + "\")");
//        System.err.println("output was " + output);

        List<Function> api = new ArrayList<Function>();

        String curFun = null, curFunDesc = "";
        Matcher matcher = Pattern.compile("1] \"(.*)\"").matcher(output);
        matcher.find();
        String funDescs = matcher.group(1);
        for (String docuLine : funDescs.split(lineBreaker)) {
            if (docuLine.startsWith(" ")) {
                curFunDesc += " " + docuLine.trim();
            } else {
                if (curFun != null) {
                    api.add(new Function(curFun, curFunDesc));
                }

                String[] splitLine = docuLine.replaceFirst(" ", "____").split("____");
                curFun = splitLine[0];
                curFunDesc = splitLine.length == 2 ? splitLine[1].trim() : "";
            }
        }

        RPackage rPackage = new RPackage(packageName);
        rPackage.addFunctions(api);

        // add function definitions
        StringBuilder getFunImplsCmd = new StringBuilder();
        for (Function function : api) {
            String funName = function.getFunName();
            getFunImplsCmd.append("print(\"" + funName + "\"); if(is.function(try(" + funName + "))) {" + funName + ";} else{ NULL};\n");
        }

        File tmpScript = File.createTempFile("rplugin", "R");
        BufferedWriter out = new BufferedWriter(new FileWriter(tmpScript));
        out.write(getFunImplsCmd.toString());
        out.close();

        String funImpls = CachingUtils.evalRScript(tmpScript);
        tmpScript.delete();

        matcher = Pattern.compile("1] \"(.*)\"\n(function.*)", Pattern.DOTALL).matcher(funImpls);
//        String[] splitFuns = funImpls.split("\n?\\[1] \"(.*)\"\n?");
        String[] splitFuns = funImpls.split("> print.*\n.*\n");

        for (int i = 0; i < api.size(); i++) {
            Function anApi = api.get(i);
            System.err.println(anApi.getFunName());
            matcher.find();

            anApi.setFunSignature(splitFuns[i + 1]);
        }

        return rPackage;
    }


    public List<RPackage> getPackagesOfFunction(String funName) {
        List<RPackage> hitList = new ArrayList<RPackage>();

        for (RPackage aPackage : packageCache) {
            if (aPackage.hasFunction(funName)) {
                hitList.add(aPackage);
            }
        }

        return hitList;
    }
}
