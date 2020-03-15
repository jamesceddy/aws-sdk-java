/*
 * Copyright 2011-2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *    http://aws.amazon.com/apache2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and
 * limitations under the License.
 */
package com.amazonaws.services.s3.transfer.internal;

import com.amazonaws.SdkClientException;
import com.amazonaws.annotation.SdkInternalApi;
import com.amazonaws.services.s3.internal.FileLocks;
import com.amazonaws.services.s3.transfer.Transfer;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Helper class to merge all the individual part files into a destinationFile.
 */
@SdkInternalApi
public class CompleteMultipartDownload implements Callable<File> {
    private final List<Future<Long>> partFiles;
    private final File destinationFile;
    private final DownloadImpl download;
    private Integer currentPartNumber;

    public CompleteMultipartDownload(List<Future<Long>> files, File destinationFile, DownloadImpl download, Integer currentPartNumber) {
        this.partFiles = files;
        this.destinationFile = destinationFile;
        this.download = download;
        this.currentPartNumber = currentPartNumber;
    }

    @Override
    public File call() throws Exception {
        int index = 0;
        try {
            for (; index < partFiles.size(); index++) {
                long filePosition = partFiles.get(index).get();
                download.updatePersistableTransfer(currentPartNumber++, filePosition);
            }
            download.setState(Transfer.TransferState.Completed);
        } catch (ExecutionException e) {
            // if any part fails, we cancel remaining part downloads and notify clients of the failure.
            for (; index < partFiles.size(); index++){
                partFiles.get(index).cancel(true);
            }
            download.setState(Transfer.TransferState.Failed);
            throw new SdkClientException(
                    "Unable to complete multi-part download. Individual part download failed : "
                            + e.getCause().getMessage(), e.getCause());

        } finally {
            FileLocks.unlock(destinationFile);
        }

        return destinationFile;
    }
}