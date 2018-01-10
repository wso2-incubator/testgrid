/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.testgrid.test.base;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Contains Util methods for integration tests.
 *
 * @since 1.0
 */
public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    // Testgrid Zip location, this is parsed as a system property from pom.xml
    public static final String TESTGRID_ZIP_LOCATION = System.getProperty("tg.zip", ".");
    // Specifies where the distribution should be unzipped, this is parsed as a system property from pom.xml
    public static final String TESTGRID_UNZIP_LOCATION = System.getProperty("project.build.directory", ".");
    // Base dir of the current project, this is parsed as a system property from pom.xml
    public static final String PROJECT_BASE_DIR = System.getProperty("basedir");
    // The location of the executable Jar from the teget directory, the path will be sanitized
    public static final String TG_EXECUTABLE_LOCATION = "/WSO2-TestGrid-*/test-grid-*.jar";

    // Testgrid execution sub commands
    public static final String GEN_TEST_PLAN_SUB_COMMAND = "generate-test-plan";
    public static final String GEN_REPORT_SUB_COMMAND = "generate-report";

    /**
     * Unzips the Testgrid distribution and prepare for execution.
     *
     * @throws IOException
     */
    public static void initialize() throws IOException, IntegrationTestException {

        extractFile(TESTGRID_ZIP_LOCATION, TESTGRID_UNZIP_LOCATION);
        copyDirectories(PROJECT_BASE_DIR + "/src/test/resources", TESTGRID_UNZIP_LOCATION + "/resources");
    }

    /**
     * Executes the generate test plan sub-command.
     *
     * @param args generate-test-plan sub-command related arguments e.g : --product
     * @return Integer valu with the execution status, 0 if success
     * @throws IntegrationTestException,IOException, {@link InterruptedException}
     */
    public static int executeGenTestPlan(String[] args)
            throws InterruptedException, IOException, IntegrationTestException {

        //Constructing the execution command for generate test plans
        String cmdCommand[] = { GEN_TEST_PLAN_SUB_COMMAND };
        String cmdArgs[] = Stream.concat(Arrays.stream(cmdCommand), Arrays.stream(args)).toArray(String[]::new);

        ShellExecutor executor = new ShellExecutor();
        return executor.executeJar(getJarPath(), cmdArgs);

    }

    /**
     * Executes the generate test report sub-command.
     *
     * @param args generate-test-plan sub-command related arguments e.g : --product
     * @return Integer valu with the execution status, 0 if success
     * @throws Exception
     */
    public static int executeGenReport(String[] args) throws Exception {
        String cmdCommand[] = { GEN_REPORT_SUB_COMMAND };
        String cmdArgs[] = Stream.concat(Arrays.stream(cmdCommand), Arrays.stream(args)).toArray(String[]::new);

        ShellExecutor executor = new ShellExecutor();
        return executor.executeJar(getJarPath(), cmdArgs);
    }

    /**
     * Construct the executable Jar absolute path and returns the value.
     *
     * @return Path of the executable Jar
     */
    private static String getJarPath() {
        // Generating the actual Jar name with the version
        String jarPath = TG_EXECUTABLE_LOCATION.replace("*", System.getProperty("project.version"));
        return TESTGRID_UNZIP_LOCATION + jarPath;
    }

    /**
     * Unzip a zip file into a given location.
     *
     * @param sourceFilePath - zip file need to extract
     * @param extractedDir   - destination path given file to extract
     * @throws IntegrationTestException is thrown if a exception occurs while unzipping the file
     */
    private static void extractFile(String sourceFilePath, String extractedDir) throws IntegrationTestException {
        logger.info("Zip location is set to " + sourceFilePath + " || Unzip path is set to " + extractedDir);
        FileOutputStream fileoutputstream = null;

        String fileDestination = extractedDir + File.separator;
        byte[] buf = new byte[1024];
        ZipInputStream zipinputstream = null;
        ZipEntry zipentry;
        try {
            zipinputstream = new ZipInputStream(new FileInputStream(sourceFilePath));

            zipentry = zipinputstream.getNextEntry();

            while (zipentry != null) {
                //for each entry to be extracted
                String entryName = fileDestination + zipentry.getName();
                entryName = entryName.replace('/', File.separatorChar);
                entryName = entryName.replace('\\', File.separatorChar);
                int n;

                File newFile = new File(entryName);
                boolean fileCreated = false;
                if (zipentry.isDirectory()) {
                    if (!newFile.exists()) {
                        fileCreated = newFile.mkdirs();
                    }
                    zipentry = zipinputstream.getNextEntry();
                    continue;
                } else {
                    File resourceFile = new File(entryName.substring(0, entryName.lastIndexOf(File.separator)));
                    if (!resourceFile.exists()) {
                        if (!resourceFile.mkdirs()) {
                            break;
                        }
                    }
                }

                fileoutputstream = new FileOutputStream(entryName);

                while ((n = zipinputstream.read(buf, 0, 1024)) > -1) {
                    fileoutputstream.write(buf, 0, n);
                }

                fileoutputstream.close();
                zipinputstream.closeEntry();
                zipentry = zipinputstream.getNextEntry();
            }
            zipinputstream.close();
        } catch (IOException e) {
            logger.error("Error occurred while unzipping the TG distribution ", e);
            throw new IntegrationTestException("Error occurred while unzipping the TG distribution ", e);

        } finally {
            try {
                if (fileoutputstream != null) {
                    fileoutputstream.close();
                }
                if (zipinputstream != null) {
                    zipinputstream.close();
                }
            } catch (IOException e) {
                // Ignore error
            }
        }
    }

    /**
     * This method will copy directories and it's contents.
     *
     * @param sourceDir Source directory to copy
     * @param targetDir Target directory to copy
     * @throws IOException is thrown if copying results an error
     */
    private static void copyDirectories(String sourceDir, String targetDir) throws IOException {

        FileUtils.copyDirectory(new File(sourceDir), new File(targetDir));

    }
}
