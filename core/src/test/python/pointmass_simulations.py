# union-intersection test of predictable Kelly vs round robin; save minimizing etas
import scipy as sp
import pandas as pd
import itertools
import random
import numpy as np
import time
from iteround import saferound
from utils import Bets, Allocations, Weights, mart, lower_confidence_bound, global_lower_bound,\
    intersection_mart, plot_marts_eta, construct_exhaustive_eta_grid, selector,\
    construct_eta_grid_plurcomp, construct_eta_bands, simulate_plurcomp, PGD, convex_uits,\
    banded_uits, brute_force_uits



alt_grid = np.linspace(0.51, 0.75, 20)
#alt_grid = [0.505, 0.51, 0.52, 0.53, 0.55, 0.6, 0.65, 0.7, 0.75]
delta_grid = [0, 0.5]
alpha = 0.05
eta_0 = 0.5
n_bands_grid = [1, 3, 10, 100, 500]

methods_list = ['uinnsm_product', 'lcb']
bets_dict = {
    "fixed_predictable":Bets.predictable_plugin,
    "agrapa":lambda x, eta: Bets.agrapa(x, eta, c = 0.9),
    "inverse": lambda x, eta: Bets.inverse_eta(x, eta, c = 0.9)}
bets_list = ["fixed_predictable", "agrapa", "inverse"]
allocations_dict = {
    "round_robin":Allocations.round_robin,
    "predictable_kelly":Allocations.predictable_kelly,
    "greedy_kelly":Allocations.greedy_kelly}
allocations_list = ["round_robin", "predictable_kelly", "greedy_kelly"]

#points = 100
K = 2
N = [200, 200]
results = []

for alt, delta, method, bet, allocation, n_bands in itertools.product(alt_grid, delta_grid, methods_list, bets_list, allocations_list, n_bands_grid):
    A_c = [alt - 0.5*delta, alt + 0.5*delta]
    if method == 'lcb':
        min_eta = None
        if (allocation in ['predictable_kelly','greedy_kelly']) or (n_bands != 1):
            stopping_time = None
            sample_size = None
        else:
            start_time = time.time()
            stopping_time, sample_size = simulate_plurcomp(
                N = N,
                A_c = A_c,
                lam_func = bets_dict[bet],
                allocation_func = allocations_dict[allocation],
                method = "lcb",
                n_bands = n_bands,
                alpha = alpha,
                WOR = False,
                reps = 1)
            run_time = time.time() - start_time
    else:
        start_time = time.time()
        stopping_time, sample_size = simulate_plurcomp(
            N = N,
            A_c = A_c,
            lam_func = bets_dict[bet],
            allocation_func = allocations_dict[allocation],
            method = "ui-ts",
            n_bands = n_bands,
            alpha = alpha,
            WOR = False,
            reps = 1)
        run_time = time.time() - start_time
    data_dict = {
        "alt":alt,
        "n_bands":n_bands,
        "delta":delta,
        "method":str(method),
        "bet":str(bet),
        "allocation":str(allocation),
        "stopping_time":stopping_time,
        "sample_size":sample_size,
        "run_time":run_time}
    results.append(data_dict)
results = pd.DataFrame(results)
results.to_csv("point_mass_results.csv", index = False)
