import { BoundingBox } from "./BoundingBox";
import { WavyLine } from "../VoiceData/Expressions/ContinuousExpressions/WavyLine";
import { GraphicalObject } from "./GraphicalObject";
export declare class GraphicalWavyLine extends GraphicalObject {
    constructor(wavyLine: WavyLine, parent: BoundingBox);
    getWavyLine: WavyLine;
}
