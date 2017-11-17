/*
 * Copyright 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "hydrogentextdetector.h"

#include <ctime>
#include <cstring>
#include <cstdlib>

#include <android/log.h>
#include <pthread.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>

#include "clusterer.h"
#include "thresholder.h"
#include "utilities.h"
#include "leptonica.h"


static int pfd[2];
static pthread_t thr;
static const char *tag = "myapp";

void *HydrogenTextDetector::thread_func(void*)
{
    ssize_t rdsz;
    char buf[128];
    while((rdsz = read(pfd[0], buf, sizeof buf - 1)) > 0) {
        if(buf[rdsz - 1] == '\n') --rdsz;
        buf[rdsz] = 0;  /* add null-terminator */
        __android_log_write(ANDROID_LOG_DEBUG, tag, buf);
    }
    return 0;
}

int HydrogenTextDetector::start_logger(const char *app_name)
{
    tag = app_name;

    /* make stdout line-buffered and stderr unbuffered */
    setvbuf(stdout, 0, _IOLBF, 0);
    setvbuf(stderr, 0, _IONBF, 0);

    /* create the pipe and redirect stdout and stderr */
    pipe(pfd);
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);

    /* spawn the logging thread */
    if(pthread_create(&thr, 0, thread_func, 0) == -1)
        return -1;
    pthread_detach(thr);
    return 0;
}


HydrogenTextDetector::HydrogenTextDetector() {
  pixs_ = NULL;
  text_areas_ = NULL;
  text_confs_ = NULL;

  const char myarray[] = "TextDetect";  // Initialization.
  HydrogenTextDetector::start_logger(myarray);
}


HydrogenTextDetector::~HydrogenTextDetector() {
  Clear();
}

PIXA *HydrogenTextDetector::ExtractTextRegions(PIX *pix8, PIX *edges, NUMA **pconfs) {
  l_int32 result;

  if (parameters_.debug) fprintf(stderr, "ExtractTextRegions()\n");

  // TODO(alanv): More error checking for invalid arguments
  if (!pconfs) {
    return NULL;
  }

  clock_t timer = clock();
  NUMA *connconfs;
  PIXA *conncomp;

  if (parameters_.debug) fprintf(stderr, "ConnCompValidPixa()\n");
  result = ConnCompValidPixa(pix8, edges, &conncomp, &connconfs, parameters_);

  if (parameters_.debug) fprintf(stderr, "Found %d connected components\n", result);

  if (parameters_.debug && parameters_.out_dir[0] != '\0' && result > 0) {
    PIX *temp = pixaDisplayHeatmap(conncomp, pix8->w, pix8->h, connconfs);
    char filename[255];
    sprintf(filename, "%s/%ld_validsingles.jpg", parameters_.out_dir, (long int) time(NULL));
    pixWriteImpliedFormat(filename, temp, 85, 0);
  }

  l_int32 count = pixaGetCount(conncomp);
  l_uint8 *remove = (l_uint8 *) calloc(count, sizeof(l_uint8));

  if (parameters_.debug) fprintf(stderr, "RemoveInvalidPairs()\n");
  result = RemoveInvalidPairs(pix8, conncomp, connconfs, remove, parameters_);

  if (parameters_.debug) fprintf(stderr, "Removed %d invalid pairs\n", result);

  if (parameters_.debug && parameters_.out_dir[0] != '\0' && result > 0) {
    PIX *temp = pixaDisplayRandomCmapFiltered(conncomp, pix8->w, pix8->h, remove);
    char filename[255];
    sprintf(filename, "%s/%ld_validpairs.jpg", parameters_.out_dir, (long int) time(NULL));
    pixWriteImpliedFormat(filename, temp, 85, 0);
  }

  NUMA *clusterconfs;
  PIXA *clusters;
  if (parameters_.debug) fprintf(stderr, "ClusterValidComponents()\n");
  result = ClusterValidComponents(pix8, conncomp, connconfs, remove, &clusters, &clusterconfs, parameters_);

  if (parameters_.debug) fprintf(stderr, "Created %d clusters\n", result);

  if (parameters_.debug && parameters_.out_dir[0] != '\0' && result > 0) {
    PIX *temp = pixaDisplayHeatmap(clusters, pix8->w, pix8->h, clusterconfs);
    char filename[255];
    sprintf(filename, "%s/%ld_validclusters.jpg", parameters_.out_dir, (long int ) time(NULL));
    pixWriteImpliedFormat(filename, temp, 85, 0);
  }

  // Merge unused components that are contained inside the detected text areas.
  // This typically catches punctuation and dots over i's and j's.
  if (parameters_.debug) fprintf(stderr, "MergePairFragments()\n");
  result = MergePairFragments(pix8, clusters, conncomp, remove);

  *pconfs = clusterconfs;

  pixaDestroy(&conncomp);
  free(remove);

  return clusters;
}

PIX *HydrogenTextDetector::DetectAndFixSkew(PIX *pixs) {
  l_float32 angle, conf;

  skew_angle_ = 0.0;

  if (!parameters_.skew_enabled) {
    if (parameters_.debug) fprintf(stderr, "Bypassed skew (skew detection is disabled)\n");

    return pixClone(pixs);
  }

  if (pixFindSkewSweepAndSearch(pixs, &angle, &conf, parameters_.skew_sweep_reduction,
                                parameters_.skew_search_reduction, parameters_.skew_sweep_range,
                                parameters_.skew_sweep_delta, parameters_.skew_search_min_delta)) {
    if (parameters_.debug) fprintf(stderr, "Bypassed skew (failed sweep and search)\n");

    return pixClone(pixs);
  }

  if (conf <= 0 || L_ABS(angle) < parameters_.skew_min_angle) {
    if (parameters_.debug) fprintf(stderr, "Bypassed skew (low confidence or small angle)\n");

    return pixClone(pixs);
  }

  if (parameters_.debug) fprintf(stderr, "Found %f degree skew with confidence %f\n", angle, conf);

  // The detected angle is the one required to align the text,
  // which is the opposite of the angle of the text itself.
  skew_angle_ = -angle;

  l_float32 deg2rad = 3.1415926535 / 180.0;
  l_float32 radians = angle * deg2rad;

  PIX *pixd = pixRotate(pixs, radians, L_ROTATE_SAMPLING, L_BRING_IN_WHITE, 0, 0);

  return pixd;
}

void HydrogenTextDetector::SetSourceImage(PIX *pixs) {
  pixs_ = pixClone(pixs);
}

void HydrogenTextDetector::DetectText() {
  if (parameters_.debug) fprintf(stderr, "DetectText()\n");

  clock_t timer = clock();

  PIX *pix8 = pixConvertTo8(pixs_, false);

  if (parameters_.debug && parameters_.out_dir[0] != '\0') {
    char filename[255];
    sprintf(filename, "%s/%ld_input.jpg", parameters_.out_dir, (long int ) time(NULL));
    pixWriteImpliedFormat(filename, pix8, 85, 0);
  }

  PIX *edges;
  pixEdgeAdaptiveThreshold(pix8, &edges, parameters_.edge_tile_x, parameters_.edge_tile_y,
                           parameters_.edge_thresh, parameters_.edge_avg_thresh);

  if (parameters_.debug && parameters_.out_dir[0] != '\0') {
    char filename[255];
    sprintf(filename, "%s/%ld_edges.jpg", parameters_.out_dir, (long int ) time(NULL));
    PIX *edges8 = pixConvertTo8(edges, false);
    pixWriteImpliedFormat(filename, edges8, 85, 0);
    pixDestroy(&edges8);
  }

  PIX *deskew = DetectAndFixSkew(edges);
  pixDestroy(&edges);

  if (parameters_.debug && parameters_.out_dir[0] != '\0') {
    char filename[255];
    sprintf(filename, "%s/%ld_deskew.jpg", parameters_.out_dir, (long int ) time(NULL));

    PIX *deskew8 = pixConvertTo8(deskew, false);
    pixWriteImpliedFormat(filename, deskew8, 85, 0);
    pixDestroy(&deskew8);
  }

  NUMA *confs;
  PIXA *clusters = ExtractTextRegions(pix8, deskew, &confs);

  if (parameters_.debug) fprintf(stderr, "Inverting image...\n");
  pixInvert(deskew, deskew);

  NUMA *invconfs;
  PIXA *invclusters = ExtractTextRegions(pix8, deskew, &invconfs);
  pixDestroy(&deskew);
  pixDestroy(&pix8);

  pixaJoin(clusters, invclusters, 0, 0);
  pixaDestroy(&invclusters);

  numaJoin(confs, invconfs);
  numaDestroy(&invconfs);

  text_areas_ = pixaCopy(clusters, L_CLONE);
  pixaDestroy(&clusters);

  text_confs_ = numaClone(confs);
  numaDestroy(&confs);

  if (parameters_.debug && parameters_.out_dir[0] != '\0') {
    PIX *temp = pixaDisplayHeatmap(text_areas_, pixs_->w, pixs_->h, text_confs_);
    char filename[255];
    sprintf(filename, "%s/heatmap.jpg", parameters_.out_dir);
    pixWriteImpliedFormat(filename, temp, 85, 0);
  }
}

void HydrogenTextDetector::Clear() {
  if (text_confs_) {
    numaDestroy(&text_confs_);
  }

  if (text_areas_) {
    pixaDestroy(&text_areas_);
  }

  if (pixs_) {
    pixDestroy(&pixs_);
  }
}

PIXA *HydrogenTextDetector::GetTextAreas() {
  return pixaCopy(text_areas_, L_CLONE);
}

l_float32 HydrogenTextDetector::GetSkewAngle() {
  return skew_angle_;
}

NUMA *HydrogenTextDetector::GetTextConfs() {
  return numaClone(text_confs_);
}

PIX *HydrogenTextDetector::GetSourceImage() {
  return pixClone(pixs_);
}

HydrogenTextDetector::TextDetectorParameters *HydrogenTextDetector::GetMutableParameters() {
  return &parameters_;
}
