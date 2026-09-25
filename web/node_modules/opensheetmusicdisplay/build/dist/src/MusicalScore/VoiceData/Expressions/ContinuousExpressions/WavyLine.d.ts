import { AbstractExpression, PlacementEnum } from "../AbstractExpression";
import { MultiExpression } from "../MultiExpression";
export declare class WavyLine extends AbstractExpression {
    constructor(placement: PlacementEnum);
    ParentStartMultiExpression: MultiExpression;
    ParentEndMultiExpression: MultiExpression;
}
